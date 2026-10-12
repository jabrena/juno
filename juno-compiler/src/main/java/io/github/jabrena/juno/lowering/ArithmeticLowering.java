package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.FloatBinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static io.github.jabrena.juno.bytecode.Opcodes.*;

/** Arithmetic, negation, shifts and bitwise operators ({@code iadd}..{@code lxor}) for every numeric type. */
final class ArithmeticLowering implements OpcodeGroup {
    private final MethodContext context;
    private final OperandStack stack;
    private final FreshValues values;

    ArithmeticLowering(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
        this.values = context.values();
    }

    @Override
    public void registerOn(OpcodeTable table) {
        table.on(IADD, instruction -> intBinary(BinaryOp.ADD));
        table.on(LADD, instruction -> longBinary(BinaryOp.ADD));
        table.on(FADD, instruction -> floatBinary(FloatBinaryOp.ADD));
        table.on(DADD, instruction -> doubleBinary(FloatBinaryOp.ADD));
        table.on(ISUB, instruction -> intBinary(BinaryOp.SUBTRACT));
        table.on(LSUB, instruction -> longBinary(BinaryOp.SUBTRACT));
        table.on(FSUB, instruction -> floatBinary(FloatBinaryOp.SUBTRACT));
        table.on(DSUB, instruction -> doubleBinary(FloatBinaryOp.SUBTRACT));
        table.on(IMUL, instruction -> intBinary(BinaryOp.MULTIPLY));
        table.on(LMUL, instruction -> longBinary(BinaryOp.MULTIPLY));
        table.on(FMUL, instruction -> floatBinary(FloatBinaryOp.MULTIPLY));
        table.on(DMUL, instruction -> doubleBinary(FloatBinaryOp.MULTIPLY));
        table.on(IDIV, instruction -> {
            guardZeroDivisor(instruction, false);
            intBinary(BinaryOp.DIVIDE);
        });
        table.on(LDIV, instruction -> {
            guardZeroDivisor(instruction, true);
            longBinary(BinaryOp.DIVIDE);
        });
        table.on(FDIV, instruction -> floatBinary(FloatBinaryOp.DIVIDE));
        table.on(DDIV, instruction -> doubleBinary(FloatBinaryOp.DIVIDE));
        table.on(IREM, instruction -> {
            guardZeroDivisor(instruction, false);
            intBinary(BinaryOp.REMAINDER);
        });
        table.on(LREM, instruction -> {
            guardZeroDivisor(instruction, true);
            longBinary(BinaryOp.REMAINDER);
        });
        table.on(FREM, instruction -> floatBinary(FloatBinaryOp.REMAINDER));
        table.on(DREM, instruction -> doubleBinary(FloatBinaryOp.REMAINDER));
        table.on(INEG, instruction -> intUnary(UnaryOp.NEGATE));
        table.on(LNEG, instruction -> longNegate());
        table.on(FNEG, instruction -> floatNegate());
        table.on(DNEG, instruction -> doubleNegate());
        table.on(ISHL, instruction -> intBinary(BinaryOp.SHIFT_LEFT));
        table.on(LSHL, instruction -> longShift(BinaryOp.SHIFT_LEFT));
        table.on(ISHR, instruction -> intBinary(BinaryOp.SHIFT_RIGHT));
        table.on(LSHR, instruction -> longShift(BinaryOp.SHIFT_RIGHT));
        table.on(IUSHR, instruction -> intBinary(BinaryOp.UNSIGNED_SHIFT_RIGHT));
        table.on(LUSHR, instruction -> longShift(BinaryOp.UNSIGNED_SHIFT_RIGHT));
        table.on(IAND, instruction -> intBinary(BinaryOp.AND));
        table.on(LAND, instruction -> longBinary(BinaryOp.AND));
        table.on(IOR, instruction -> intBinary(BinaryOp.OR));
        table.on(LOR, instruction -> longBinary(BinaryOp.OR));
        table.on(IXOR, instruction -> intBinary(BinaryOp.XOR));
        table.on(LXOR, instruction -> longBinary(BinaryOp.XOR));
    }

    private void intUnary(UnaryOp operation) {
        Value operand = stack.pop();
        Value result = values.int32();
        context.emit(new IrInstruction.Unary(result, operation, operand));
        stack.push(result);
    }

    private void intBinary(BinaryOp operation) {
        Value right = stack.pop();
        Value left = stack.pop();
        Value result = values.int32();
        context.emit(new IrInstruction.Binary(result, operation, left, right));
        stack.push(result);
    }

    private void floatBinary(FloatBinaryOp operation) {
        Value right = stack.popFloat();
        Value left = stack.popFloat();
        Value result = values.float32();
        context.emit(new IrInstruction.FloatBinary(result, operation, left, right));
        stack.push(result);
    }

    private void doubleBinary(FloatBinaryOp operation) {
        Value right = stack.popDouble();
        Value left = stack.popDouble();
        Value result = values.float64();
        context.emit(new IrInstruction.DoubleBinary(result, operation, left, right));
        stack.push(result);
    }

    private void floatNegate() {
        Value operand = stack.popFloat();
        Value result = values.float32();
        context.emit(new IrInstruction.FloatNegate(result, operand));
        stack.push(result);
    }

    private void doubleNegate() {
        Value operand = stack.popDouble();
        Value result = values.float64();
        context.emit(new IrInstruction.DoubleNegate(result, operand));
        stack.push(result);
    }

    private void longBinary(BinaryOp operation) {
        OperandStack.LongHalves right = stack.popHalves();
        OperandStack.LongHalves left = stack.popHalves();
        Value low = values.int32();
        Value high = values.int32();
        context.emit(new IrInstruction.LongBinary(low, high, operation, left.low(), left.high(), right.low(),
                right.high()));
        stack.pushHalves(low, high);
    }

    /** {@code lshl}/{@code lshr}/{@code lushr}: the shift amount is a plain int, popped before the long value. */
    private void longShift(BinaryOp operation) {
        Value amount = stack.pop();
        OperandStack.LongHalves value = stack.popHalves();
        Value low = values.int32();
        Value high = values.int32();
        context.emit(new IrInstruction.LongShift(low, high, operation, value.low(), value.high(), amount));
        stack.pushHalves(low, high);
    }

    private void longNegate() {
        OperandStack.LongHalves value = stack.popHalves();
        Value low = values.int32();
        Value high = values.int32();
        context.emit(new IrInstruction.LongNegate(low, high, value.low(), value.high()));
        stack.pushHalves(low, high);
    }

    /**
     * Java throws {@code ArithmeticException("/ by zero")} for an integer {@code /} or {@code %} by zero. Where a
     * handler in this method catches it, or the program lets it unwind to callers, the current IR block ends here
     * with a zero test: the zero path builds that exception and jumps to the handler (known statically, since the
     * exception's class is), or leaves it pending and returns; the rest of the bytecode block continues in a new IR
     * block. Elsewhere the division is left alone and a zero divisor still panics.
     */
    private void guardZeroDivisor(Instruction instruction, boolean wide) {
        OptionalInt handler = context.exceptions().arithmeticHandlerAt(instruction.offset());
        if (handler.isEmpty() && !context.program().divisionByZeroUnwinds()) {
            return;
        }
        Value divisor;
        if (wide) {
            Value low = stack.peekInt(2);
            Value high = stack.peekInt(1);
            divisor = values.int32();
            context.emit(new IrInstruction.Binary(divisor, BinaryOp.OR, low, high));
        } else {
            divisor = stack.peekInt(1);
        }
        Value zero = values.int32();
        context.emit(new IrInstruction.Const(zero, 0));
        Value isZero = values.int32();
        context.emit(new IrInstruction.Compare(isZero, Condition.EQUAL, divisor, zero));
        int continuation = IrBlockWriter.SYNTHETIC_BLOCK_BASE + 2 * instruction.offset();
        int thrower = continuation + 1;
        context.code().split(new IrTerminator.Branch(isZero, thrower, continuation), continuation);

        Value exception = values.int32();
        Value message = values.int32();
        IrInstruction delivery = handler.isPresent()
                ? new IrInstruction.StoreLocal(stack.bottomSlot(), exception)
                : new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.THROW_RAISE, Optional.empty(),
                        List.of(exception), List.of());
        context.code().add(new IrBasicBlock(thrower, List.of(
                new IrInstruction.NewObject(exception, ExceptionRouting.ARITHMETIC_EXCEPTION),
                new IrInstruction.StringConst(message, "/ by zero"),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.THROWABLE_SET_MESSAGE,
                        Optional.of(exception), List.of(message), List.of()),
                delivery),
                new IrTerminator.Jump(handler.orElse(IrBlockWriter.PROPAGATE_BLOCK))));
    }
}
