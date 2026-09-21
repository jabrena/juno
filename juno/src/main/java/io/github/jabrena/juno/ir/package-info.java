/**
 * Juno's own intermediate representation: a non-SSA, block-structured form that
 * {@code lowering} produces from JVM bytecode, {@code optimize} transforms, and {@code backend}
 * consumes to generate assembly.
 *
 * <p>A {@link io.github.jabrena.juno.ir.IrProgram} holds every reachable
 * {@link io.github.jabrena.juno.ir.IrMethod}, each a sequence of
 * {@link io.github.jabrena.juno.ir.IrBasicBlock}s of
 * {@link io.github.jabrena.juno.ir.IrInstruction}s ending in an
 * {@link io.github.jabrena.juno.ir.IrTerminator}. Every operand-stack push/pop and local
 * variable becomes an explicit, typed {@link io.github.jabrena.juno.ir.Value} — a symbolic virtual
 * register produced by exactly one instruction — carrying a {@link io.github.jabrena.juno.ir.JunoType}.
 * {@link io.github.jabrena.juno.ir.BinaryOp}, {@link io.github.jabrena.juno.ir.FloatBinaryOp},
 * {@link io.github.jabrena.juno.ir.UnaryOp}, and {@link io.github.jabrena.juno.ir.Condition} name
 * the operations an instruction can perform; {@link io.github.jabrena.juno.ir.ArrayDeclaration}
 * and {@link io.github.jabrena.juno.ir.ArrayElementType} describe a method's hoisted local arrays
 * and their storage width.
 */
package io.github.jabrena.juno.ir;
