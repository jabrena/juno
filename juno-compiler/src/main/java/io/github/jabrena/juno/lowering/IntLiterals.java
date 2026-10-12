package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ConstantPool;
import org.jspecify.annotations.Nullable;

import static io.github.jabrena.juno.bytecode.Opcodes.BIPUSH;
import static io.github.jabrena.juno.bytecode.Opcodes.ICONST_0;
import static io.github.jabrena.juno.bytecode.Opcodes.ICONST_5;
import static io.github.jabrena.juno.bytecode.Opcodes.ICONST_M1;
import static io.github.jabrena.juno.bytecode.Opcodes.LDC;
import static io.github.jabrena.juno.bytecode.Opcodes.LDC_W;
import static io.github.jabrena.juno.bytecode.Opcodes.SIPUSH;

/** Reads the int literal an instruction pushes, resolving {@code ldc} through the class's constant pool. */
record IntLiterals(ConstantPool constantPool) {
    /** The int {@code instruction} pushes, or {@code null} when it does not push an int literal. */
    @Nullable Integer pushedBy(Instruction instruction) {
        int opcode = instruction.opcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) {
            return opcode - ICONST_0;
        }
        return switch (opcode) {
            case BIPUSH, SIPUSH -> instruction.operandA();
            case LDC, LDC_W -> constantPool.integer(instruction.operandA());
            default -> null;
        };
    }
}
