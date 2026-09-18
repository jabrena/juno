package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;

import java.util.List;
import java.util.Optional;

/** One value-producing or state-mutating operation lowered from JVM bytecode. */
public sealed interface IrInstruction {
    record Const(Value target, int value) implements IrInstruction {
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
}
