package io.github.jabrena.juno.bytecode;

import java.util.List;

public record Instruction(int offset, int opcode, int operandA, int operandB,
                          List<Integer> switchKeys, List<Integer> switchOffsets) {
    public Instruction(int offset, int opcode, int operandA, int operandB) {
        this(offset, opcode, operandA, operandB, List.of(), List.of());
    }

    public Instruction {
        switchKeys = List.copyOf(switchKeys);
        switchOffsets = List.copyOf(switchOffsets);
    }
}
