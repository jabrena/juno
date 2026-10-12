package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;

import java.util.function.BiFunction;

import static io.github.jabrena.juno.bytecode.Opcodes.*;

/** Numeric conversions ({@code i2l}..{@code i2s}) and the {@code long}/{@code float}/{@code double} comparisons. */
final class ConversionLowering implements OpcodeGroup {
    private final MethodContext context;
    private final OperandStack stack;
    private final FreshValues values;

    ConversionLowering(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
        this.values = context.values();
    }

    @Override
    public void registerOn(OpcodeTable table) {
        table.on(I2L, instruction -> pushLong(stack.pop(), IrInstruction.IntToLong::new));
        table.on(I2F, instruction -> push(stack.pop(), values.float32(), IrInstruction.IntToFloat::new));
        table.on(I2D, instruction -> push(stack.pop(), values.float64(), IrInstruction.IntToDouble::new));
        table.on(L2I, instruction -> pushFromLong(JunoType.INT32, IrInstruction.LongToInt::new));
        table.on(L2F, instruction -> pushFromLong(JunoType.FLOAT32, IrInstruction.LongToFloat::new));
        table.on(L2D, instruction -> pushFromLong(JunoType.FLOAT64, IrInstruction.LongToDouble::new));
        table.on(F2I, instruction -> push(stack.popFloat(), values.int32(), IrInstruction.FloatToInt::new));
        table.on(F2L, instruction -> pushLong(stack.popFloat(), IrInstruction.FloatToLong::new));
        table.on(F2D, instruction -> push(stack.popFloat(), values.float64(), IrInstruction.FloatToDouble::new));
        table.on(D2I, instruction -> push(stack.popDouble(), values.int32(), IrInstruction.DoubleToInt::new));
        table.on(D2L, instruction -> pushLong(stack.popDouble(), IrInstruction.DoubleToLong::new));
        table.on(D2F, instruction -> push(stack.popDouble(), values.float32(), IrInstruction.DoubleToFloat::new));
        table.on(I2B, instruction -> narrow(UnaryOp.TO_BYTE));
        table.on(I2C, instruction -> narrow(UnaryOp.TO_CHAR));
        table.on(I2S, instruction -> narrow(UnaryOp.TO_SHORT));
        table.on(LCMP, instruction -> compareLongs());
        table.on(FCMPL, FCMPG, instruction -> {
            Value right = stack.popFloat();
            Value left = stack.popFloat();
            int nanResult = instruction.opcode() == FCMPL ? -1 : 1;
            Value result = values.int32();
            context.emit(new IrInstruction.FloatCompare(result, left, right, nanResult));
            stack.push(result);
        });
        table.on(DCMPL, DCMPG, instruction -> {
            Value right = stack.popDouble();
            Value left = stack.popDouble();
            int nanResult = instruction.opcode() == DCMPL ? -1 : 1;
            Value result = values.int32();
            context.emit(new IrInstruction.DoubleCompare(result, left, right, nanResult));
            stack.push(result);
        });
    }

    /** A conversion from one value: {@code operand} is popped before {@code result} is allocated. */
    private void push(Value operand, Value result, BiFunction<Value, Value, IrInstruction> conversion) {
        context.emit(conversion.apply(result, operand));
        stack.push(result);
    }

    /** A conversion from {@code long}: the operand's halves are popped before the result is allocated. */
    private void pushFromLong(JunoType resultType, LongConversion conversion) {
        OperandStack.LongHalves operand = stack.popHalves();
        Value result = values.of(resultType);
        context.emit(conversion.apply(result, operand.low(), operand.high()));
        stack.push(result);
    }

    /** A conversion to {@code long}, whose result is two 32-bit halves. */
    private void pushLong(Value operand, ToLongConversion conversion) {
        Value low = values.int32();
        Value high = values.int32();
        context.emit(conversion.apply(low, high, operand));
        stack.pushHalves(low, high);
    }

    private void compareLongs() {
        OperandStack.LongHalves right = stack.popHalves();
        OperandStack.LongHalves left = stack.popHalves();
        Value result = values.int32();
        context.emit(new IrInstruction.LongCompare(result, left.low(), left.high(), right.low(), right.high()));
        stack.push(result);
    }

    @FunctionalInterface
    private interface LongConversion {
        IrInstruction apply(Value result, Value low, Value high);
    }

    @FunctionalInterface
    private interface ToLongConversion {
        IrInstruction apply(Value low, Value high, Value operand);
    }

    private void narrow(UnaryOp operation) {
        Value operand = stack.pop();
        Value result = values.int32();
        context.emit(new IrInstruction.Unary(result, operation, operand));
        stack.push(result);
    }

}
