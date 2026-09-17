package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.bytecode.Instruction;

import java.util.List;

/** A maximal run of instructions with one entry point ({@code start}) and one exit ({@code terminator}). */
public record BasicBlock(int start, List<Instruction> instructions, Terminator terminator) {
}
