package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.linker.ConstantTables;
import io.github.jabrena.juno.linker.LambdaSite;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One value-producing or state-mutating operation lowered from JVM bytecode. */
public sealed interface IrInstruction {
    /** Every value this instruction reads or defines. */
    default List<Value> referencedValues() {
        return switch (this) {
            case IrInstruction.Const constant -> List.of(constant.target());
            case IrInstruction.StringConst constant -> List.of(constant.target());
            case IrInstruction.FloatConst constant -> List.of(constant.target());
            case IrInstruction.DoubleConst constant -> List.of(constant.target());
            case IrInstruction.LoadLocal load -> List.of(load.target());
            case IrInstruction.StoreLocal store -> List.of(store.value());
            case IrInstruction.LoadStatic load -> List.of(load.target());
            case IrInstruction.StoreStatic store -> List.of(store.value());
            case IrInstruction.NewObject object -> List.of(object.target());
            case IrInstruction.LambdaCreate lambda -> {
                List<Value> values = new ArrayList<>();
                values.add(lambda.target());
                values.addAll(lambda.captures());
                yield List.copyOf(values);
            }
            case IrInstruction.LoadField load -> List.of(load.target(), load.receiver());
            case IrInstruction.StoreField store -> List.of(store.receiver(), store.value());
            case IrInstruction.IntArrayConst array -> List.of(array.target());
            case IrInstruction.ConstantTableRef table -> List.of(table.target());
            case IrInstruction.NewMultiArray array -> List.of(array.target());
            case IrInstruction.Panic ignored -> List.of();
            case IrInstruction.NullCheck check -> List.of(check.value());
            case IrInstruction.Binary binary -> List.of(binary.target(), binary.left(), binary.right());
            case IrInstruction.Unary unary -> List.of(unary.target(), unary.value());
            case IrInstruction.Compare compare -> List.of(compare.target(), compare.left(), compare.right());
            case IrInstruction.Call call -> {
                List<Value> values = new ArrayList<>();
                call.target().ifPresent(values::add);
                values.addAll(call.arguments());
                yield List.copyOf(values);
            }
            case IrInstruction.LambdaCall call -> {
                List<Value> values = new ArrayList<>();
                call.target().ifPresent(values::add);
                values.addAll(call.arguments());
                yield List.copyOf(values);
            }
            case IrInstruction.InterfaceCall call -> {
                List<Value> values = new ArrayList<>();
                call.target().ifPresent(values::add);
                values.addAll(call.arguments());
                yield List.copyOf(values);
            }
            case IrInstruction.IntrinsicCall call -> {
                List<Value> values = new ArrayList<>();
                call.target().ifPresent(values::add);
                call.receiver().ifPresent(values::add);
                values.addAll(call.arguments());
                yield List.copyOf(values);
            }
            case IrInstruction.NewArray newArray -> List.of(newArray.target());
            case IrInstruction.ArrayLoad load -> List.of(load.target(), load.array(), load.index());
            case IrInstruction.ArrayStore store -> List.of(store.array(), store.index(), store.value());
            case IrInstruction.BoundsCheck check -> List.of(check.index());
            case IrInstruction.LongConst constant -> List.of(constant.targetLow(), constant.targetHigh());
            case IrInstruction.LongBinary binary -> List.of(binary.targetLow(), binary.targetHigh(),
                    binary.leftLow(), binary.leftHigh(), binary.rightLow(), binary.rightHigh());
            case IrInstruction.LongShift shift -> List.of(shift.targetLow(), shift.targetHigh(),
                    shift.valueLow(), shift.valueHigh(), shift.shiftAmount());
            case IrInstruction.LongNegate negate -> List.of(negate.targetLow(), negate.targetHigh(),
                    negate.valueLow(), negate.valueHigh());
            case IrInstruction.LongCompare compare -> List.of(compare.target(), compare.leftLow(),
                    compare.leftHigh(), compare.rightLow(), compare.rightHigh());
            case IrInstruction.IntToLong widen -> List.of(widen.targetLow(), widen.targetHigh(), widen.value());
            case IrInstruction.LongToInt narrow -> List.of(narrow.target(), narrow.valueLow(), narrow.valueHigh());
            case IrInstruction.FloatBinary binary -> List.of(binary.target(), binary.left(), binary.right());
            case IrInstruction.FloatNegate negate -> List.of(negate.target(), negate.value());
            case IrInstruction.FloatCompare compare -> List.of(compare.target(), compare.left(), compare.right());
            case IrInstruction.IntToFloat conversion -> List.of(conversion.target(), conversion.value());
            case IrInstruction.FloatToInt conversion -> List.of(conversion.target(), conversion.value());
            case IrInstruction.DoubleBinary binary -> List.of(binary.target(), binary.left(), binary.right());
            case IrInstruction.DoubleNegate negate -> List.of(negate.target(), negate.value());
            case IrInstruction.DoubleCompare compare -> List.of(compare.target(), compare.left(), compare.right());
            case IrInstruction.IntToDouble conversion -> List.of(conversion.target(), conversion.value());
            case IrInstruction.DoubleToInt conversion -> List.of(conversion.target(), conversion.value());
            case IrInstruction.FloatToDouble conversion -> List.of(conversion.target(), conversion.value());
            case IrInstruction.DoubleToFloat conversion -> List.of(conversion.target(), conversion.value());
            case IrInstruction.LongToDouble conversion ->
                    List.of(conversion.target(), conversion.valueLow(), conversion.valueHigh());
            case IrInstruction.DoubleToLong conversion ->
                    List.of(conversion.targetLow(), conversion.targetHigh(), conversion.value());
            case IrInstruction.PackLong packed -> List.of(packed.target(), packed.valueLow(), packed.valueHigh());
            case IrInstruction.UnpackLong unpacked ->
                    List.of(unpacked.targetLow(), unpacked.targetHigh(), unpacked.value());
            case IrInstruction.LongToFloat conversion ->
                    List.of(conversion.target(), conversion.valueLow(), conversion.valueHigh());
            case IrInstruction.FloatToLong conversion ->
                    List.of(conversion.targetLow(), conversion.targetHigh(), conversion.value());
        };
    }

    record Const(Value target, int value) implements IrInstruction {
    }

    /** A UTF-8 string literal materialized as a null-terminated runtime string reference. */
    record StringConst(Value target, String value) implements IrInstruction {
    }

    record FloatConst(Value target, float value) implements IrInstruction {
    }

    record DoubleConst(Value target, double value) implements IrInstruction {
    }

    record LoadLocal(Value target, int local) implements IrInstruction {
    }

    record StoreLocal(int local, Value value) implements IrInstruction {
    }

    record LoadStatic(Value target, FieldRef field) implements IrInstruction {
    }

    record StoreStatic(FieldRef field, Value value) implements IrInstruction {
    }

    record NewObject(Value target, String className) implements IrInstruction {
    }

    /** Materializes a lambda function reference, allocating only when captured values need storage. */
    record LambdaCreate(Value target, LambdaSite site, List<Value> captures) implements IrInstruction {
        public LambdaCreate {
            captures = List.copyOf(captures);
        }
    }

    record LoadField(Value target, FieldRef field, Value receiver) implements IrInstruction {
    }

    record StoreField(FieldRef field, Value receiver, Value value) implements IrInstruction {
    }

    /** A compiler-created immutable int/reference array, used for enum values and switch maps. */
    record IntArrayConst(Value target, List<Integer> values) implements IrInstruction {
    }

    /** The address of a read-only {@code static final} lookup table placed in flash (see {@link ConstantTables}). */
    record ConstantTableRef(Value target, ConstantTables.Table table) implements IrInstruction {
    }

    /** A fixed-size multidimensional primitive array allocated recursively from the arena. */
    record NewMultiArray(Value target, ArrayElementType leafType, List<Integer> dimensions)
            implements IrInstruction {
    }

    record Panic() implements IrInstruction {
    }

    /** Runtime null guard used by javac for bound method references. */
    record NullCheck(Value value) implements IrInstruction {
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

    /** Invokes a resolved lambda adapter; {@code arguments} starts with the lambda reference. */
    record LambdaCall(Optional<Value> target, LambdaSite site, List<Value> arguments) implements IrInstruction {
        public LambdaCall {
            arguments = List.copyOf(arguments);
        }
    }

    /** A polymorphic interface call; {@code arguments} starts with the receiver. */
    record InterfaceCall(Optional<Value> target, List<Value> arguments, List<InterfaceTarget> targets)
            implements IrInstruction {
        public InterfaceCall {
            arguments = List.copyOf(arguments);
            targets = List.copyOf(targets);
            if (arguments.isEmpty() || targets.size() < 2) {
                throw new IllegalArgumentException("Polymorphic interface calls need a receiver and two targets");
            }
        }
    }

    /**
     * A call to a hardware operation; {@code receiver} is present only for instance-style intrinsics.
     * {@code literalArguments} holds this call's {@code String}-typed arguments, in their own left-to-right
     * order, resolved to compile-time literal text. {@code arguments} holds every other argument,
     * including the explicitly supported runtime-String parameters, likewise in left-to-right order.
     */
    record IntrinsicCall(Optional<Value> target, Intrinsic intrinsic, Optional<Value> receiver,
                          List<Value> arguments, List<String> literalArguments) implements IrInstruction {
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

    record DoubleBinary(Value target, FloatBinaryOp operation, Value left, Value right) implements IrInstruction {
    }

    record DoubleNegate(Value target, Value value) implements IrInstruction {
    }

    /** JVM {@code dcmpl}/{@code dcmpg}; {@code nanResult} is respectively -1 or 1. */
    record DoubleCompare(Value target, Value left, Value right, int nanResult) implements IrInstruction {
        public DoubleCompare {
            if (nanResult != -1 && nanResult != 1) {
                throw new IllegalArgumentException("Double comparison NaN result must be -1 or 1");
            }
        }
    }

    record IntToDouble(Value target, Value value) implements IrInstruction {
    }

    record DoubleToInt(Value target, Value value) implements IrInstruction {
    }

    record FloatToDouble(Value target, Value value) implements IrInstruction {
    }

    record DoubleToFloat(Value target, Value value) implements IrInstruction {
    }

    record LongToDouble(Value target, Value valueLow, Value valueHigh) implements IrInstruction {
    }

    record DoubleToLong(Value targetLow, Value targetHigh, Value value) implements IrInstruction {
    }

    /** Packs Juno's two stack/local halves into a native 64-bit call/field/array boundary value. */
    record PackLong(Value target, Value valueLow, Value valueHigh) implements IrInstruction {
    }

    /** Splits a native 64-bit boundary value back into Juno's two stack/local halves. */
    record UnpackLong(Value targetLow, Value targetHigh, Value value) implements IrInstruction {
    }

    record LongToFloat(Value target, Value valueLow, Value valueHigh) implements IrInstruction {
    }

    record FloatToLong(Value targetLow, Value targetHigh, Value value) implements IrInstruction {
    }
}
