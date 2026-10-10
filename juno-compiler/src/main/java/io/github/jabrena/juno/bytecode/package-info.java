/**
 * Decodes the intentionally small JVM bytecode subset Juno v0.1 supports.
 *
 * <p>{@link io.github.jabrena.juno.bytecode.BytecodeDecoder} turns a
 * {@link io.github.jabrena.juno.classfile.JavaMethod}'s raw {@code code} array into a sequence of
 * {@link io.github.jabrena.juno.bytecode.Instruction}s (offset, opcode, and up to two operands,
 * plus resolved {@code tableswitch}/{@code lookupswitch} keys and targets), validating along the
 * way that every opcode encountered is one Juno recognizes. This is a purely syntactic decode step
 * — {@code linker} and {@code lowering} give the resulting instructions their control-flow and
 * IR meaning.
 */
package io.github.jabrena.juno.bytecode;
