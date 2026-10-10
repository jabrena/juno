package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ConstantPool;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only lookup tables kept in flash ({@code .rodata}) instead of the 8 KiB arena: a {@code static final}
 * one-dimensional primitive array whose {@code <clinit>} initializer is all compile-time constants
 * ({@code static final short[] T = {1, 2, 3};}) and which every reachable method only ever reads as
 * {@code T[i]} or {@code T.length}.
 *
 * <p>Flash cannot be written by an ordinary store, so the read-only rule is checked on the bytecode, never
 * assumed: each {@code getstatic} of a candidate is followed through straight-line code until the value is
 * consumed, and it must be consumed as the array operand of an array load or {@code arraylength}. A table
 * passed to a method, stored in a local or field, written to, or used across a branch keeps the old
 * behaviour (allocated in the arena by its {@code <clinit>}), and {@link #ramFallbacks} says why, so a
 * program that compiled before still compiles unchanged.
 */
public final class ConstantTables {
    private static final int GETSTATIC = 178;
    private static final int PUTSTATIC = 179;

    /** One flash-resident table: element values as raw bits, sign-extended for the integer types. */
    public record Table(FieldRef field, List<Long> values) {
        public Table {
            values = List.copyOf(values);
        }

        /** The element's descriptor character, e.g. {@code S} for a {@code short[]}. */
        public char elementDescriptor() {
            return Descriptor.arrayElementDescriptor(field.descriptor());
        }

        public int length() {
            return values.size();
        }
    }

    /** A {@code static final} constant-initialized array that has to stay in the arena, and why. */
    public record RamFallback(FieldRef field, MethodRef method, String reason) {
    }

    private record Candidate(Table table, MethodRef initializer, Set<Integer> offsets) {
    }

    private final Map<FieldRef, Table> tables;
    private final Map<MethodRef, Set<Integer>> initializerOffsets;
    private final List<RamFallback> ramFallbacks;

    private ConstantTables(Map<FieldRef, Table> tables, Map<MethodRef, Set<Integer>> initializerOffsets,
                           List<RamFallback> ramFallbacks) {
        this.tables = tables;
        this.initializerOffsets = initializerOffsets;
        this.ramFallbacks = ramFallbacks;
    }

    /** No tables at all, for lowering a single method outside a whole program. */
    public static ConstantTables none() {
        return new ConstantTables(Map.of(), Map.of(), List.of());
    }

    public static ConstantTables of(Program program) {
        return analyze(program.methods());
    }

    public static ConstantTables analyze(List<LinkedMethod> methods) {
        Map<FieldRef, Candidate> candidates = new LinkedHashMap<>();
        for (LinkedMethod linked : methods) {
            if (linked.method().reference().name().equals("<clinit>")) {
                findInitializers(linked, candidates);
            }
        }
        List<RamFallback> fallbacks = new ArrayList<>();
        for (LinkedMethod linked : methods) {
            List<Instruction> instructions = linked.instructions();
            for (int index = 0; index < instructions.size() && !candidates.isEmpty(); index++) {
                Instruction instruction = instructions.get(index);
                if (instruction.opcode() != GETSTATIC && instruction.opcode() != PUTSTATIC) {
                    continue;
                }
                FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
                Candidate candidate = candidates.get(field);
                if (candidate == null) {
                    continue;
                }
                String reason = fallbackReason(linked, index, candidate);
                if (reason != null) {
                    candidates.remove(field);
                    fallbacks.add(new RamFallback(field, linked.method().reference(), reason));
                }
            }
        }
        Map<FieldRef, Table> tables = new LinkedHashMap<>();
        Map<MethodRef, Set<Integer>> offsets = new HashMap<>();
        for (Candidate candidate : candidates.values()) {
            tables.put(candidate.table().field(), candidate.table());
            offsets.computeIfAbsent(candidate.initializer(), unused -> new TreeSet<>()).addAll(candidate.offsets());
        }
        return new ConstantTables(Map.copyOf(tables), Map.copyOf(offsets), List.copyOf(fallbacks));
    }

    /** The flash table {@code field} names, or {@code null} when it is an ordinary static field. */
    public @Nullable Table table(FieldRef field) {
        return tables.get(field);
    }

    /** Bytecode offsets of {@code method}'s instructions that only built a flash table; lowering skips them. */
    public Set<Integer> initializerOffsets(MethodRef method) {
        return initializerOffsets.getOrDefault(method, Set.of());
    }

    public List<RamFallback> ramFallbacks() {
        return ramFallbacks;
    }

    /** Each {@code static final} primitive array the {@code <clinit>} builds from javac's constant initializer. */
    private static void findInitializers(LinkedMethod linked, Map<FieldRef, Candidate> candidates) {
        List<Instruction> instructions = linked.instructions();
        ConstantPool pool = linked.owner().constantPool();
        for (int start = 0; start < instructions.size(); start++) {
            TableBytecode.Initializer initializer = TableBytecode.initializer(instructions, pool, start);
            if (initializer == null) {
                continue;
            }
            int end = initializer.putstatic();
            FieldRef field = pool.fieldRef(instructions.get(end).operandA());
            if (isTableField(field, linked.owner(), TableBytecode.elementOfAtype(instructions.get(start + 1).operandA()))) {
                Set<Integer> offsets = new TreeSet<>();
                for (int index = start; index <= end; index++) {
                    offsets.add(instructions.get(index).offset());
                }
                List<Long> values = new ArrayList<>(initializer.values().length);
                for (long value : initializer.values()) {
                    values.add(value);
                }
                candidates.put(field, new Candidate(new Table(field, values), linked.method().reference(), offsets));
            }
            start = end;
        }
    }

    private static boolean isTableField(FieldRef field, JavaClass owner, char element) {
        char declared = field.descriptor().length() == 2 ? field.descriptor().charAt(1) : 0;
        if (!field.owner().equals(owner.name()) || declared != element && !(element == 'B' && declared == 'Z')) {
            return false;
        }
        FieldInfo declaration = owner.findField(field.name(), field.descriptor());
        return declaration != null && declaration.isStatic() && declaration.isFinal();
    }

    /** Why the field access at {@code index} keeps {@code candidate} out of flash, or {@code null} if it does not. */
    private static @Nullable String fallbackReason(LinkedMethod linked, int index, Candidate candidate) {
        Instruction instruction = linked.instructions().get(index);
        if (instruction.opcode() == PUTSTATIC) {
            boolean initializer = candidate.initializer().equals(linked.method().reference())
                    && candidate.offsets().contains(instruction.offset());
            return initializer ? null : "is assigned again at bytecode offset " + instruction.offset();
        }
        return TableReads.readOnly(linked, index) ? null : "is used at bytecode offset " + instruction.offset()
                + " other than as T[i] or T.length (passed on, stored, written or read across a branch)";
    }
}
