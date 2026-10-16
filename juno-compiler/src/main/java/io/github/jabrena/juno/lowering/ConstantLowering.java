package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.classfile.ConstantPool;

import static io.github.jabrena.juno.bytecode.Opcodes.*;

/** Constant pushes: {@code aconst_null}, {@code iconst_*}..{@code dconst_*}, {@code bipush}/{@code sipush}, {@code ldc*}. */
final class ConstantLowering implements OpcodeGroup {
    private final MethodContext context;

    ConstantLowering(MethodContext context) {
        this.context = context;
    }

    @Override
    public void registerOn(OpcodeTable table) {
        OperandStack stack = context.stack();
        table.on(NOP, instruction -> { });
        table.on(ACONST_NULL, instruction -> stack.pushInt(0));
        table.on(ICONST_M1, ICONST_5, instruction -> stack.pushInt(instruction.opcode() - ICONST_0));
        table.on(LCONST_0, LCONST_1, instruction -> stack.pushLong(instruction.opcode() - LCONST_0));
        table.on(FCONST_0, FCONST_2, instruction -> stack.pushFloat(instruction.opcode() - FCONST_0));
        table.on(DCONST_0, DCONST_1, instruction -> stack.pushDouble(instruction.opcode() - DCONST_0));
        table.on(BIPUSH, SIPUSH, instruction -> stack.pushInt(instruction.operandA()));
        table.on(LDC, LDC_W, instruction -> pushConstant(instruction.operandA()));
        table.on(LDC2_W, instruction -> pushWideConstant(instruction.operandA()));
    }

    /** {@code ldc}: an {@code int}, {@code float} or string constant. */
    private void pushConstant(int index) {
        ConstantPool pool = context.constantPool();
        if (pool.isFloat(index)) {
            context.stack().pushFloat(pool.floatValue(index));
        } else if (pool.isString(index)) {
            context.stack().pushString(pool.string(index));
        } else if (pool.isClass(index)) {
            // Juno has no Class objects; the linker admits a class literal only in a namespace's loader, which
            // hands it to Util.loadWithClass, and that call is lowered to nothing.
            context.stack().pushInt(0);
        } else {
            context.stack().pushInt(pool.integer(index));
        }
    }

    /** {@code ldc2_w}: a {@code long} or {@code double} constant. */
    private void pushWideConstant(int index) {
        ConstantPool pool = context.constantPool();
        if (pool.isDouble(index)) {
            context.stack().pushDouble(pool.doubleValue(index));
        } else {
            context.stack().pushLong(pool.longValue(index));
        }
    }
}
