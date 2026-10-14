package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;

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
 * Lays out one method's frame: a stack word for every local slot some instruction loads or stores, and a slot for
 * every value that is not a rematerialized constant, shared between block-local values whose live intervals do not
 * overlap (see {@link FrameLayout}).
 */
final class FrameAllocator {
    private final IrMethod method;
    private final int pushBytes;
    private final int alignment;

    /**
     * @param pushBytes bytes the prologue pushes above the frame, which count toward its alignment
     * @param alignment the stack alignment the frame plus {@code pushBytes} must keep
     */
    FrameAllocator(IrMethod method, int pushBytes, int alignment) {
        this.method = method;
        this.pushBytes = pushBytes;
        this.alignment = alignment;
    }

    FrameLayout layout() {
        int numLocals = method.maxLocals();
        Map<Integer, Integer> constants = constants();
        ValueStorage valueStorage = allocateValues(constants);
        int[] valueOffsets = valueStorage.offsets();
        int valueBytes = valueStorage.bytes();
        int[] localOffsets = new int[numLocals];
        // JVMS 2.6.1 defines the local-variable array in slots. int/float/reference use one slot;
        // long/double reserve two consecutive slots. The lowering preserves those indices, so one
        // 32-bit word per JVM slot is both sufficient and exact. Only slots some instruction loads or
        // stores get a word (a double's second slot with its first), so slots the optimizer emptied cost nothing.
        boolean[] used = usedLocals(numLocals);
        int localWords = 0;
        for (int i = 0; i < numLocals; i++) {
            localOffsets[i] = used[i] ? valueBytes + localWords++ * FrameLayout.WORD : -1;
        }
        int rawSize = valueBytes + localWords * FrameLayout.WORD;
        // PUSH_BYTES (36) isn't itself a multiple of 8; round the *total* prologue adjustment up to
        // the next multiple of 8 so `sp` stays AAPCS-aligned for every `bl`, then subtract PUSH_BYTES
        // back out to get the frame size to actually `sub sp` by.
        int frameSize = roundUp(pushBytes + rawSize, alignment) - pushBytes;
        return new FrameLayout(frameSize, valueOffsets, localOffsets, constants);
    }

    private boolean[] usedLocals(int numLocals) {
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

    private void markUsed(boolean[] used, int local, JunoType type) {
        used[local] = true;
        if (type == JunoType.FLOAT64 && local + 1 < used.length) {
            used[local + 1] = true;
        }
    }

    /** Values a single {@code Const} defines; a value defined twice (never produced by lowering) keeps its slot. */
    private Map<Integer, Integer> constants() {
        Map<Integer, Integer> constants = new HashMap<>();
        Set<Integer> conflicting = new HashSet<>();
        for (var block : method.blocks()) {
            for (var instruction : block.instructions()) {
                if (instruction instanceof IrInstruction.Const constant && !constant.target().type().isWide()) {
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
    private ValueStorage allocateValues(Map<Integer, Integer> constants) {
        int valueCount = method.values().size();
        int[] blocksSeen = new int[valueCount];
        List<BlockIntervals> blocks = new ArrayList<>(method.blocks().size());

        for (var block : method.blocks()) {
            int[] first = new int[valueCount];
            int[] last = new int[valueCount];
            Arrays.fill(first, -1);
            int position = 0;
            for (var instruction : block.instructions()) {
                recordOccurrences(instruction.referencedValues(), position++, first, last);
            }
            recordOccurrences(block.terminator().referencedValues(), position, first, last);
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
                int widthWords = valueWidth(interval.value().type()) / FrameLayout.WORD;
                int word = firstFreeRun(occupied, widthWords);
                Arrays.fill(occupied, word, word + widthWords, true);
                offsets[interval.value().id()] = dedicatedBytes + word * FrameLayout.WORD;
                active.add(new ActiveInterval(interval.last(), word, widthWords));
                blockWords = Math.max(blockWords, word + widthWords);
            }
            sharedWords = Math.max(sharedWords, blockWords);
        }
        return new ValueStorage(offsets, dedicatedBytes + sharedWords * FrameLayout.WORD);
    }

    private void recordOccurrences(List<Value> values, int position, int[] first, int[] last) {
        for (Value value : values) {
            int id = value.id();
            if (first[id] < 0) {
                first[id] = position;
            }
            last[id] = position;
        }
    }

    private int firstFreeRun(boolean[] occupied, int width) {
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
    private int valueWidth(JunoType type) {
        return type.isWide() ? 8 : FrameLayout.WORD;
    }

    // Private holders built and read only in this class; the arrays are never compared or hashed.
    @SuppressWarnings("ArrayRecordComponent")
    private record BlockIntervals(int[] first, int[] last) {
    }

    private record Interval(Value value, int first, int last) {
    }

    private record ActiveInterval(int last, int word, int widthWords) {
    }

    @SuppressWarnings("ArrayRecordComponent")
    private record ValueStorage(int[] offsets, int bytes) {
    }

    private int roundUp(int value, int multiple) {
        return ((value + multiple - 1) / multiple) * multiple;
    }
}
