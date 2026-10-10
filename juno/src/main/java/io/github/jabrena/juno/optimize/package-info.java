/**
 * IR-to-IR optimization passes run between lowering and the backend.
 *
 * <p>Each pass implements the single-method {@link io.github.jabrena.juno.optimize.CompilerPass}
 * interface, so {@code CompilationPipeline} can run them as an ordered, independently testable
 * list. {@link io.github.jabrena.juno.optimize.ConstantFolder} folds arithmetic/comparison
 * instructions with already-known-constant operands into a single constant, and a branch with a
 * constant condition into an unconditional jump. {@link io.github.jabrena.juno.optimize.CopyPropagation}
 * eliminates a local load whose value an earlier store in the same block already determined.
 * {@link io.github.jabrena.juno.optimize.DeadBlockElimination} then removes any basic block a
 * folded branch left unreachable from its method's entry block.
 *
 * <p>Copy propagation uses a conservative CFG meet: a JVM/synthetic local crosses a block boundary
 * only when every predecessor contains the same typed IR value. Conflicting merge values stay as
 * loads. The other passes reason only within a single basic block.
 */
package io.github.jabrena.juno.optimize;
