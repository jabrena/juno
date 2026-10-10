package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.IrValues;
import io.github.jabrena.juno.ir.Value;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every value/local's fixed stack offset, and the total (8-byte-aligned) frame size to reserve. A value defined by
 * exactly one {@link IrInstruction.Const} is rematerialized instead: it has no slot, and every use loads its
 * immediate (or folds it into the instruction), so the constant is never stored and reloaded.
 */
record FrameLayout(int frameSize, int[] valueOffsets, int[] localOffsets, Map<Integer, Integer> constants) {
    FrameLayout {
        constants = Map.copyOf(constants);
    }

    int valueOffset(Value value) {
        if (constants.containsKey(value.id())) {
            throw new IllegalStateException("constant " + value + " is rematerialized and has no stack slot");
        }
        return valueOffsets[value.id()];
    }

    /** The value's constant, when it is a rematerialized {@code Const}; otherwise null. */
    @Nullable Integer constant(Value value) {
        return constants.get(value.id());
    }

    int localOffset(int local) {
        if (localOffsets[local] < 0) {
            throw new IllegalStateException("local slot " + local + " is never loaded or stored and has no stack word");
        }
        return localOffsets[local];
    }

    static FrameLayout of(IrMethod method) {
        List<Value> values = method.values();
        int numValues = values.size();
        int numLocals = method.maxLocals();
        Map<Integer, Integer> constants = constants(method);
        ValueStorage valueStorage = allocateValues(method, constants);
        int[] valueOffsets = valueStorage.offsets();
        int valueBytes = valueStorage.bytes();
        int[] localOffsets = new int[numLocals];
        // JVMS 2.6.1 defines the local-variable array in slots. int/float/reference use one slot;
        // long/double reserve two consecutive slots. The lowering preserves those indices, so one
        // 32-bit word per JVM slot is both sufficient and exact. Only slots some instruction loads or
        // stores get a word (a double's second slot with its first), so slots the optimizer emptied cost nothing.
        boolean[] used = usedLocals(method, numLocals);
        int localWords = 0;
        for (int i = 0; i < numLocals; i++) {
            localOffsets[i] = used[i] ? valueBytes + localWords++ * AsmEmitter.WORD : -1;
        }
        int rawSize = valueBytes + localWords * AsmEmitter.WORD;
        // PUSH_BYTES (36) isn't itself a multiple of 8; round the *total* prologue adjustment up to
        // the next multiple of 8 so `sp` stays AAPCS-aligned for every `bl`, then subtract PUSH_BYTES
        // back out to get the frame size to actually `sub sp` by.
        int frameSize = AsmEmitter.roundUp(AsmEmitter.PUSH_BYTES + rawSize, 8) - AsmEmitter.PUSH_BYTES;
        return new FrameLayout(frameSize, valueOffsets, localOffsets, constants);
    }

    private static boolean[] usedLocals(IrMethod method, int numLocals) {
        boolean[] used = new boolean[numLocals];
        for (var block : method.blocks()) {
            for (var instruction : block.instructions()) {
                if (instruction instanceof IrInstruction.LoadLocal load) {
                    markUsed(used, load.local(), load.target().type());
                } else if (instruction instanceof IrInstruction.StoreLocal store) {
                    markUsed(used, store.local(), store.value().type());
                }
            }
        }
        return used;
    }

    private static void markUsed(boolean[] used, int local, JunoType type) {
        used[local] = true;
        if (type == JunoType.FLOAT64 && local + 1 < used.length) {
            used[local + 1] = true;
        }
    }

    /** Values a single {@code Const} defines; a value defined twice (never produced by lowering) keeps its slot. */
    private static Map<Integer, Integer> constants(IrMethod method) {
        Map<Integer, Integer> constants = new HashMap<>();
        Set<Integer> conflicting = new HashSet<>();
        for (var block : method.blocks()) {
            for (var instruction : block.instructions()) {
                if (instruction instanceof IrInstruction.Const constant && !isWide(constant.target().type())) {
                    Integer previous = constants.put(constant.target().id(), constant.value());
                    if (previous != null) {
                        conflicting.add(constant.target().id());
                    }
                }
            }
        }
        conflicting.forEach(constants::remove);
        return constants;
    }

    /**
     * Reuses storage for block-local SSA values whose live intervals do not overlap. A value
     * referenced from more than one basic block keeps a dedicated slot, which makes the allocation
     * conservative across branches and loop backedges without requiring a global register allocator.
     */
    private static ValueStorage allocateValues(IrMethod method, Map<Integer, Integer> constants) {
        int valueCount = method.values().size();
        int[] blocksSeen = new int[valueCount];
        List<BlockIntervals> blocks = new ArrayList<>(method.blocks().size());

        for (var block : method.blocks()) {
            int[] first = new int[valueCount];
            int[] last = new int[valueCount];
            Arrays.fill(first, -1);
            int position = 0;
            for (var instruction : block.instructions()) {
                recordOccurrences(IrValues.of(instruction), position++, first, last);
            }
            recordOccurrences(IrValues.of(block.terminator()), position, first, last);
            for (int id = 0; id < valueCount; id++) {
                if (first[id] >= 0) {
                    blocksSeen[id]++;
                }
            }
            blocks.add(new BlockIntervals(first, last));
        }

        int[] offsets = new int[valueCount];
        int dedicatedBytes = 0;
        for (Value value : method.values()) {
            if (blocksSeen[value.id()] > 1 && !constants.containsKey(value.id())) {
                offsets[value.id()] = dedicatedBytes;
                dedicatedBytes += valueWidth(value.type());
            }
        }

        int sharedWords = 0;
        for (BlockIntervals block : blocks) {
            List<Interval> intervals = new ArrayList<>();
            for (Value value : method.values()) {
                int id = value.id();
                if (blocksSeen[id] == 1 && block.first()[id] >= 0 && !constants.containsKey(id)) {
                    intervals.add(new Interval(value, block.first()[id], block.last()[id]));
                }
            }
            intervals.sort(Comparator.comparingInt(Interval::first)
                    .thenComparingInt(interval -> interval.value().id()));

            List<ActiveInterval> active = new ArrayList<>();
            boolean[] occupied = new boolean[Math.max(1, valueCount * 2)];
            int blockWords = 0;
            for (Interval interval : intervals) {
                for (Iterator<ActiveInterval> iterator = active.iterator(); iterator.hasNext();) {
                    ActiveInterval candidate = iterator.next();
                    if (candidate.last() < interval.first()) {
                        Arrays.fill(occupied, candidate.word(), candidate.word() + candidate.widthWords(), false);
                        iterator.remove();
                    }
                }
                int widthWords = valueWidth(interval.value().type()) / AsmEmitter.WORD;
                int word = firstFreeRun(occupied, widthWords);
                Arrays.fill(occupied, word, word + widthWords, true);
                offsets[interval.value().id()] = dedicatedBytes + word * AsmEmitter.WORD;
                active.add(new ActiveInterval(interval.last(), word, widthWords));
                blockWords = Math.max(blockWords, word + widthWords);
            }
            sharedWords = Math.max(sharedWords, blockWords);
        }
        return new ValueStorage(offsets, dedicatedBytes + sharedWords * AsmEmitter.WORD);
    }

    private static void recordOccurrences(List<Value> values, int position, int[] first, int[] last) {
        for (Value value : values) {
            int id = value.id();
            if (first[id] < 0) {
                first[id] = position;
            }
            last[id] = position;
        }
    }

    private static int firstFreeRun(boolean[] occupied, int width) {
        for (int start = 0; start <= occupied.length - width; start++) {
            boolean free = true;
            for (int word = start; word < start + width; word++) {
                if (occupied[word]) {
                    free = false;
                    start = word;
                    break;
                }
            }
            if (free) {
                return start;
            }
        }
        throw new IllegalStateException("IR value storage exceeds its conservative word bound");
    }

    /**
     * {@code long}/{@code double} boundary {@link Value}s ({@link JunoType#INT64}/{@link JunoType#FLOAT64})
     * need 8 bytes; everything else (including {@link JunoType#FLOAT32}, a raw bit pattern) fits in 4.
     */
    private static int valueWidth(JunoType type) {
        return isWide(type) ? 8 : AsmEmitter.WORD;
    }

    static boolean isWide(JunoType type) {
        return type == JunoType.INT64 || type == JunoType.FLOAT64;
    }

    private record BlockIntervals(int[] first, int[] last) {
    }

    private record Interval(Value value, int first, int last) {
    }

    private record ActiveInterval(int last, int word, int widthWords) {
    }

    private record ValueStorage(int[] offsets, int bytes) {
    }
}
