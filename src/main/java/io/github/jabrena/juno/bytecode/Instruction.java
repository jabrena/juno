package io.github.jabrena.juno.bytecode;

public record Instruction(int offset, int opcode, int operandA, int operandB) {
}
