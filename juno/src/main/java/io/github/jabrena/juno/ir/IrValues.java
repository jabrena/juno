package io.github.jabrena.juno.ir;

import java.util.ArrayList;
import java.util.List;

/** Extracts every value referenced or produced by an IR node for method-level validation. */
final class IrValues {
    private IrValues() {
    }

    static List<Value> of(IrInstruction instruction) {
        return switch (instruction) {
            case IrInstruction.Const constant -> List.of(constant.target());
            case IrInstruction.FloatConst constant -> List.of(constant.target());
            case IrInstruction.DoubleConst constant -> List.of(constant.target());
            case IrInstruction.LoadLocal load -> List.of(load.target());
            case IrInstruction.StoreLocal store -> List.of(store.value());
            case IrInstruction.LoadStatic load -> List.of(load.target());
            case IrInstruction.StoreStatic store -> List.of(store.value());
            case IrInstruction.Binary binary -> List.of(binary.target(), binary.left(), binary.right());
            case IrInstruction.Unary unary -> List.of(unary.target(), unary.value());
            case IrInstruction.Compare compare -> List.of(compare.target(), compare.left(), compare.right());
            case IrInstruction.Call call -> {
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

    static List<Value> of(IrTerminator terminator) {
        return switch (terminator) {
            case IrTerminator.Jump ignored -> List.of();
            case IrTerminator.Branch branch -> List.of(branch.condition());
            case IrTerminator.Return returned -> returned.value().stream().toList();
        };
    }

}
