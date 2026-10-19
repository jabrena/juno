package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;

import java.util.function.BiFunction;

import static io.github.jabrena.juno.bytecode.Opcodes.D2F;
import static io.github.jabrena.juno.bytecode.Opcodes.D2I;
import static io.github.jabrena.juno.bytecode.Opcodes.D2L;
import static io.github.jabrena.juno.bytecode.Opcodes.DCMPG;
import static io.github.jabrena.juno.bytecode.Opcodes.DCMPL;
import static io.github.jabrena.juno.bytecode.Opcodes.F2D;
import static io.github.jabrena.juno.bytecode.Opcodes.F2I;
import static io.github.jabrena.juno.bytecode.Opcodes.F2L;
import static io.github.jabrena.juno.bytecode.Opcodes.FCMPG;
import static io.github.jabrena.juno.bytecode.Opcodes.FCMPL;
import static io.github.jabrena.juno.bytecode.Opcodes.I2B;
import static io.github.jabrena.juno.bytecode.Opcodes.I2C;
import static io.github.jabrena.juno.bytecode.Opcodes.I2D;
import static io.github.jabrena.juno.bytecode.Opcodes.I2F;
import static io.github.jabrena.juno.bytecode.Opcodes.I2L;
import static io.github.jabrena.juno.bytecode.Opcodes.I2S;
import static io.github.jabrena.juno.bytecode.Opcodes.L2D;
import static io.github.jabrena.juno.bytecode.Opcodes.L2F;
import static io.github.jabrena.juno.bytecode.Opcodes.L2I;
import static io.github.jabrena.juno.bytecode.Opcodes.LCMP;

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

    /** The {@code l2f} conversion, for the Clojure runtime's {@code (float x)} cast on a long literal. */
    void longToFloat() {
        pushFromLong(JunoType.FLOAT32, IrInstruction.LongToFloat::new);
    }

    /** The {@code d2f} narrowing, for the Clojure runtime's {@code (float x)} cast (see {@link ClojureLowering}). */
    void doubleToFloat() {
        push(stack.popDouble(), values.float32(), IrInstruction.DoubleToFloat::new);
    }

    /** The {@code l2i} narrowing, for the Clojure runtime's int casts (see {@link ClojureLowering}). */
    void longToInt() {
        pushFromLong(JunoType.INT32, IrInstruction.LongToInt::new);
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

    /**
     * A long comparison that yields a boolean, for the Clojure runtime's {@code Numbers.lt} and friends: {@code lcmp}
     * of the two longs, then the condition against zero.
     */
    void longComparison(Condition condition) {
        compareLongs();
        Value order = stack.pop();
        Value zero = values.int32();
        context.emit(new IrInstruction.Const(zero, 0));
        Value result = values.int32();
        context.emit(new IrInstruction.Compare(result, condition, order, zero));
        stack.push(result);
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
