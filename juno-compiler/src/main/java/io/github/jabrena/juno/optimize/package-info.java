/**
 * IR-to-IR optimization passes run between lowering and the backend.
 *
 * <p>Each pass implements the single-method {@link io.github.jabrena.juno.optimize.CompilerPass}
 * interface, so {@code CompilationPipeline} can run them as an ordered, independently testable
 * list. {@link io.github.jabrena.juno.optimize.ConstantFolder} folds arithmetic/comparison
 * instructions with already-known-constant operands into a single constant, and a branch with a
 * constant condition into an unconditional jump. {@link io.github.jabrena.juno.optimize.CopyPropagation}
 * eliminates a local load whose value is already known, from an earlier store in its block or from every
 * predecessor.
 * {@link io.github.jabrena.juno.optimize.DeadBlockElimination} then removes any basic block a
 * folded branch left unreachable from its method's entry block, and
 * {@link io.github.jabrena.juno.optimize.DeadLocalStoreElimination} removes stores to local slots
 * no remaining instruction reads.
 *
 * <p>Copy propagation uses a conservative CFG meet: a JVM/synthetic local crosses a block boundary
 * only when every predecessor contains the same typed IR value. Conflicting merge values stay as
 * loads. Other method-wide passes only use properties independent of predecessor identity, such as
 * whether a local slot has any read at all.
 */
@NullMarked
package io.github.jabrena.juno.optimize;

import org.jspecify.annotations.NullMarked;
