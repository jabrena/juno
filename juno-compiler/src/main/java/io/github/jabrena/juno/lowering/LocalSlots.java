package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.LinkedMethod;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.jabrena.juno.bytecode.Opcodes.ANEWARRAY;
import static io.github.jabrena.juno.bytecode.Opcodes.ASTORE;
import static io.github.jabrena.juno.bytecode.Opcodes.ASTORE_0;
import static io.github.jabrena.juno.bytecode.Opcodes.ASTORE_3;
import static io.github.jabrena.juno.bytecode.Opcodes.NEWARRAY;

/**
 * What one method's JVM local variables are known to hold. A local assigned via {@code astore} exactly once in the
 * whole method ("effectively final") can only ever hold that one value, since Java requires definite assignment
 * before any read; so when that value is an array created with a compile-time-constant size, or a string literal,
 * every load of the local carries that fact without a merge-aware dataflow pass.
 */
final class LocalSlots {
    private final Set<Integer> singleAssignment;
    private final Map<Integer, Integer> arrayLengths;
    private final Map<Integer, String> strings = new HashMap<>();

    LocalSlots(LinkedMethod method) {
        List<Instruction> instructions = method.instructions();
        Map<Integer, Long> stores = instructions.stream()
                .map(this::astoreSlot)
                .filter(slot -> slot != null)
                .collect(Collectors.groupingBy(slot -> slot, Collectors.counting()));
        this.singleAssignment = stores.entrySet().stream()
                .filter(entry -> entry.getValue() == 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
        this.arrayLengths = arrayLengths(instructions, new IntLiterals(method.owner().constantPool()));
    }

    /** The length of the array {@code local} always holds, or {@code null} when it is not known. */
    @Nullable Integer knownArrayLength(int local) {
        return arrayLengths.get(local);
    }

    /** The string literal {@code local} always holds, or {@code null} when it is not known. */
    @Nullable String knownString(int local) {
        return strings.get(local);
    }

    /** {@code value} was stored to {@code local}: remembers it when it is a literal the local will always hold. */
    void rememberStore(int local, Value value, ValueTracking facts) {
        String literal = facts.knownString(value);
        if (literal != null && singleAssignment.contains(local)) {
            strings.put(local, literal);
        }
    }

    /** Single-assignment locals stored right after a {@code newarray}/{@code anewarray} of a constant size. */
    private Map<Integer, Integer> arrayLengths(List<Instruction> instructions, IntLiterals literals) {
        Map<Integer, Integer> lengths = new HashMap<>();
        for (int index = 2; index < instructions.size(); index++) {
            Integer slot = astoreSlot(instructions.get(index));
            Instruction allocation = instructions.get(index - 1);
            boolean primitiveArray = allocation.opcode() == NEWARRAY
                    && ArrayElementType.fromAtype(allocation.operandA()).isPresent();
            if (slot == null || !(primitiveArray || allocation.opcode() == ANEWARRAY)) {
                continue;
            }
            Integer length = literals.pushedBy(instructions.get(index - 2));
            if (length != null) {
                lengths.put(slot, length);
            }
        }
        lengths.keySet().retainAll(singleAssignment);
        return Map.copyOf(lengths);
    }

    private @Nullable Integer astoreSlot(Instruction instruction) {
        int opcode = instruction.opcode();
        if (opcode == ASTORE) {
            return instruction.operandA();
        }
        return opcode >= ASTORE_0 && opcode <= ASTORE_3 ? opcode - ASTORE_0 : null;
    }
}
