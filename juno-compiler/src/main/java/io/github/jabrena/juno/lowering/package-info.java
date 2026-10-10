/**
 * Lowers linked, reachable JVM bytecode into Juno's IR.
 *
 * <p>{@link io.github.jabrena.juno.lowering.BytecodeToIr} is the single class here: for each
 * reachable method it walks the {@link io.github.jabrena.juno.analysis.ControlFlowGraph}'s basic
 * blocks and turns every {@link io.github.jabrena.juno.bytecode.Instruction} into
 * {@link io.github.jabrena.juno.ir.IrInstruction}s, representing the JVM operand stack as
 * synthetic local slots so that a value pushed by one predecessor block and consumed by a common
 * successor is threaded through correctly. This is also where a hardware API call is resolved to
 * its {@link io.github.jabrena.juno.intrinsic.Intrinsic} and where enum/record-specific bytecode
 * shapes (ordinal lookups, accessor calls) get their Juno-specific meaning.
 */
package io.github.jabrena.juno.lowering;
