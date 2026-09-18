package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;

import java.util.List;
import java.util.Optional;

/** One value-producing or state-mutating operation lowered from JVM bytecode. */
public sealed interface IrInstruction {
    record Const(Value target, int value) implements IrInstruction {
    }

    record FloatConst(Value target, float value) implements IrInstruction {
    }

    record LoadLocal(Value target, int local) implements IrInstruction {
    }

    record StoreLocal(int local, Value value) implements IrInstruction {
    }

    record Binary(Value target, BinaryOp operation, Value left, Value right) implements IrInstruction {
    }

    record Unary(Value target, UnaryOp operation, Value value) implements IrInstruction {
    }

    record Compare(Value target, Condition condition, Value left, Value right) implements IrInstruction {
    }

    /** A call to a reachable user-defined static method. */
    record Call(Optional<Value> target, MethodRef method, List<Value> arguments) implements IrInstruction {
    }

    /** A call to a hardware operation; {@code receiver} is present only for instance-style intrinsics. */
    record IntrinsicCall(Optional<Value> target, Intrinsic intrinsic, Optional<Value> receiver,
                          List<Value> arguments) implements IrInstruction {
    }

    /** Declares a fixed-size local array; {@code target} becomes a handle to it. */
    record NewArray(Value target, ArrayElementType elementType, int length) implements IrInstruction {
    }

    record ArrayLoad(Value target, ArrayElementType elementType, Value array, Value index) implements IrInstruction {
    }

    record ArrayStore(ArrayElementType elementType, Value array, Value index, Value value) implements IrInstruction {
    }

    /** Panics if {@code index} is outside {@code [0, length)}; emitted only when {@code length} is known. */
    record BoundsCheck(Value index, int length) implements IrInstruction {
    }

    /**
     * A 64-bit {@code long} value, materialized as two 32-bit halves ({@code targetLow}/{@code targetHigh})
     * since every JVM local/stack slot and every backend {@code int32_t} is 32-bit. Long support is
     * restricted to locals and arithmetic (no parameters, returns, fields, or arrays of long).
     */
    record LongConst(Value targetLow, Value targetHigh, long value) implements IrInstruction {
    }

    record LongBinary(Value targetLow, Value targetHigh, BinaryOp operation,
                       Value leftLow, Value leftHigh, Value rightLow, Value rightHigh) implements IrInstruction {
    }

    /** {@code shiftAmount} is a plain (32-bit) int, per JVM {@code lshl}/{@code lshr}/{@code lushr} semantics. */
    record LongShift(Value targetLow, Value targetHigh, BinaryOp operation,
                      Value valueLow, Value valueHigh, Value shiftAmount) implements IrInstruction {
    }

    record LongNegate(Value targetLow, Value targetHigh, Value valueLow, Value valueHigh) implements IrInstruction {
    }

    /** {@code lcmp}: produces a regular int in {-1, 0, 1}, which then flows through ordinary int branch opcodes. */
    record LongCompare(Value target, Value leftLow, Value leftHigh, Value rightLow, Value rightHigh)
            implements IrInstruction {
    }

    record IntToLong(Value targetLow, Value targetHigh, Value value) implements IrInstruction {
    }

    /** {@code l2i}: truncates to the low 32 bits, which is exactly {@code valueLow} by construction. */
    record LongToInt(Value target, Value valueLow, Value valueHigh) implements IrInstruction {
    }

    record FloatBinary(Value target, FloatBinaryOp operation, Value left, Value right) implements IrInstruction {
    }

    record FloatNegate(Value target, Value value) implements IrInstruction {
    }

    /** JVM {@code fcmpl}/{@code fcmpg}; {@code nanResult} is respectively -1 or 1. */
    record FloatCompare(Value target, Value left, Value right, int nanResult) implements IrInstruction {
        public FloatCompare {
            if (nanResult != -1 && nanResult != 1) {
                throw new IllegalArgumentException("Float comparison NaN result must be -1 or 1");
            }
        }
    }

    record IntToFloat(Value target, Value value) implements IrInstruction {
    }

    record FloatToInt(Value target, Value value) implements IrInstruction {
    }
}
