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
 * <p>Every pass deliberately reasons only within a single basic block — JVM operand-stack
 * positions are represented as local slots that different predecessor blocks can store different
 * values into, so seeing across a block boundary soundly would need a separate CFG data-flow
 * analysis these passes don't attempt.
 */
package io.github.jabrena.juno.optimize;
