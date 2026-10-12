package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;

/** Lowers one bytecode instruction of the method being lowered into IR (see {@link MethodLowering}). */
@FunctionalInterface
interface InstructionLowering {
    void lower(Instruction instruction);
}
