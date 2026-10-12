package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.IrProgram;

/**
 * Generates assembly and its runtime shim for one instruction-set architecture. A backend keeps per-program state
 * while it generates, so each instance generates exactly one program.
 */
public interface AsmBackend {
    GeneratedCode generate(IrProgram program);
}
