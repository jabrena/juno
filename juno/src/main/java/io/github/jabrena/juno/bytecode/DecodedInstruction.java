package io.github.jabrena.juno.bytecode;

import java.util.List;

/** One decoded instruction's shape, before it is anchored to its bytecode offset in {@link Instruction}. */
record DecodedInstruction(int opcode, int length, int operandA, int operandB,
                           List<Integer> switchKeys, List<Integer> switchOffsets) {
    static DecodedInstruction of(int opcode, int length) {
        return new DecodedInstruction(opcode, length, 0, 0, List.of(), List.of());
    }

    static DecodedInstruction of(int opcode, int length, int operandA, int operandB) {
        return new DecodedInstruction(opcode, length, operandA, operandB, List.of(), List.of());
    }

    static DecodedInstruction ofSwitch(int opcode, int length, int operandA,
                                       List<Integer> switchKeys, List<Integer> switchOffsets) {
        return new DecodedInstruction(opcode, length, operandA, 0, switchKeys, switchOffsets);
    }
}
