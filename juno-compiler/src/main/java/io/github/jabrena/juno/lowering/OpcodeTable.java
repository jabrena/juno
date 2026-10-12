package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;

/** Which {@link InstructionLowering} lowers each opcode: the dispatch table one method's lowering runs through. */
final class OpcodeTable {
    private final InstructionLowering[] lowerings = new InstructionLowering[256];

    /** Lowers {@code opcode} with {@code lowering}. */
    void on(int opcode, InstructionLowering lowering) {
        lowerings[opcode] = lowering;
    }

    /** Lowers every opcode from {@code first} to {@code last} (inclusive) with {@code lowering}. */
    void on(int first, int last, InstructionLowering lowering) {
        for (int opcode = first; opcode <= last; opcode++) {
            on(opcode, lowering);
        }
    }

    void lower(Instruction instruction) {
        InstructionLowering lowering = lowerings[instruction.opcode()];
        if (lowering == null) {
            throw new CompileException("Juno IR lowering does not support opcode " + instruction.opcode());
        }
        lowering.lower(instruction);
    }
}
