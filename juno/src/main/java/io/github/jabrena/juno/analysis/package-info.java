/**
 * Control-flow structure and conservative resource/runtime-risk analysis over decoded bytecode.
 *
 * <p>{@link io.github.jabrena.juno.analysis.ControlFlowGraphBuilder} partitions a method's
 * decoded {@link io.github.jabrena.juno.bytecode.Instruction}s into
 * {@link io.github.jabrena.juno.analysis.BasicBlock}s, resolving every branch target into an
 * explicit {@link io.github.jabrena.juno.analysis.Terminator} edge; the result is a
 * {@link io.github.jabrena.juno.analysis.ControlFlowGraph}. {@code lowering} consumes this
 * structure directly when computing an operand stack's true entry depth at each block.
 *
 * <p>{@link io.github.jabrena.juno.analysis.RuntimeRiskAnalyzer} walks the optimized, closed-world
 * IR to produce a {@link io.github.jabrena.juno.analysis.RuntimeRiskReport}: fixed-arena budget
 * versus estimated usage, static RAM, maximum call depth, emitted bounds checks, and stable
 * {@link io.github.jabrena.juno.analysis.RuntimeRisk} findings (allocation inside loops,
 * recursion, possible division by zero, unchecked array access, compile-time-null dereferences),
 * each carrying a {@link io.github.jabrena.juno.analysis.RiskSeverity}. This backs the CLI's
 * {@code juno inspect --risks} command; the Arduino linker's own memory report remains
 * authoritative for final RAM/flash use.
 */
package io.github.jabrena.juno.analysis;
