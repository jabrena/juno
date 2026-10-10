package io.github.jabrena.juno.ir;

import java.util.List;

/** A block of lowered IR instructions ending in a single {@link IrTerminator}. */
public record IrBasicBlock(int start, List<IrInstruction> instructions, IrTerminator terminator) {
}
