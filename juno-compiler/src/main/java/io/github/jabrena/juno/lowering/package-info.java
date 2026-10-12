/**
 * Lowers linked, reachable JVM bytecode into Juno's IR.
 *
 * <p>{@link io.github.jabrena.juno.lowering.BytecodeToIr} is the entry point. It computes what every method needs to
 * know about the program ({@code ProgramContext}) and lowers each method with a {@code MethodLowering}, which hands
 * every bytecode instruction to the lowerer of its opcode group ({@code ConstantLowering},
 * {@code ArithmeticLowering}, {@code InvokeLowering}, …). The lowerers share one {@code MethodContext}: the method's
 * {@code OperandStack} (the JVM operand stack kept in synthetic local slots, so a value pushed by one predecessor
 * block and consumed by a common successor is threaded through correctly), the {@code IrBlockWriter} that collects
 * the IR, and the facts known about its values. This is also where a hardware API call is resolved to its
 * {@link io.github.jabrena.juno.intrinsic.Intrinsic} and where enum-specific bytecode shapes (ordinal lookups,
 * switch maps) get their Juno-specific meaning.
 */
@NullMarked
package io.github.jabrena.juno.lowering;

import org.jspecify.annotations.NullMarked;
