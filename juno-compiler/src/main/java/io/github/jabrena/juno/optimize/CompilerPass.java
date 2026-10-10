package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.IrProgram;

/** One IR-to-IR transformation in the optimization pipeline, run between lowering and the backend. */
public interface CompilerPass {
    IrProgram apply(IrProgram program);
}
