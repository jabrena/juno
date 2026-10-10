package io.github.jabrena.juno.ir;

import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

/**
 * Rebuilds IR nodes with their values, local slots and block targets mapped: every value an instruction defines or
 * reads goes through {@code values}, every local slot through {@code locals}, and every jump target through
 * {@code blocks}. Passes that rename (inlining) or substitute (copy propagation) share this one exhaustive walk.
 */
public final class IrRewriting {
    private IrRewriting() {
    }

    public static IrInstruction map(IrInstruction instruction, UnaryOperator<Value> values, IntUnaryOperator locals) {
        return switch (instruction) {
            case IrInstruction.Const node -> new IrInstruction.Const(values.apply(node.target()), node.value());
            case IrInstruction.StringConst node -> new IrInstruction.StringConst(values.apply(node.target()), node.value());
            case IrInstruction.FloatConst node -> new IrInstruction.FloatConst(values.apply(node.target()), node.value());
            case IrInstruction.DoubleConst node -> new IrInstruction.DoubleConst(values.apply(node.target()), node.value());
            case IrInstruction.LoadLocal node ->
                    new IrInstruction.LoadLocal(values.apply(node.target()), locals.applyAsInt(node.local()));
            case IrInstruction.StoreLocal node ->
                    new IrInstruction.StoreLocal(locals.applyAsInt(node.local()), values.apply(node.value()));
            case IrInstruction.LoadStatic node -> new IrInstruction.LoadStatic(values.apply(node.target()), node.field());
            case IrInstruction.StoreStatic node -> new IrInstruction.StoreStatic(node.field(), values.apply(node.value()));
            case IrInstruction.NewObject node -> new IrInstruction.NewObject(values.apply(node.target()), node.className());
            case IrInstruction.LambdaCreate node -> new IrInstruction.LambdaCreate(values.apply(node.target()),
                    node.site(), all(node.captures(), values));
            case IrInstruction.LoadField node -> new IrInstruction.LoadField(values.apply(node.target()), node.field(),
                    values.apply(node.receiver()));
            case IrInstruction.StoreField node -> new IrInstruction.StoreField(node.field(),
                    values.apply(node.receiver()), values.apply(node.value()));
            case IrInstruction.IntArrayConst node ->
                    new IrInstruction.IntArrayConst(values.apply(node.target()), node.values());
            case IrInstruction.ConstantTableRef node ->
                    new IrInstruction.ConstantTableRef(values.apply(node.target()), node.table());
            case IrInstruction.NewMultiArray node -> new IrInstruction.NewMultiArray(values.apply(node.target()),
                    node.leafType(), node.dimensions());
            case IrInstruction.Panic node -> node;
            case IrInstruction.NullCheck node -> new IrInstruction.NullCheck(values.apply(node.value()));
            case IrInstruction.Binary node -> new IrInstruction.Binary(values.apply(node.target()), node.operation(),
                    values.apply(node.left()), values.apply(node.right()));
            case IrInstruction.Unary node -> new IrInstruction.Unary(values.apply(node.target()), node.operation(),
                    values.apply(node.value()));
            case IrInstruction.Compare node -> new IrInstruction.Compare(values.apply(node.target()), node.condition(),
                    values.apply(node.left()), values.apply(node.right()));
            case IrInstruction.Call node -> new IrInstruction.Call(optional(node.target(), values), node.method(),
                    all(node.arguments(), values));
            case IrInstruction.LambdaCall node -> new IrInstruction.LambdaCall(optional(node.target(), values),
                    node.site(), all(node.arguments(), values));
            case IrInstruction.InterfaceCall node -> new IrInstruction.InterfaceCall(optional(node.target(), values),
                    all(node.arguments(), values), node.targets());
            case IrInstruction.IntrinsicCall node -> new IrInstruction.IntrinsicCall(optional(node.target(), values),
                    node.intrinsic(), optional(node.receiver(), values), all(node.arguments(), values),
                    node.literalArguments());
            case IrInstruction.NewArray node -> new IrInstruction.NewArray(values.apply(node.target()),
                    node.elementType(), node.length());
            case IrInstruction.ArrayLoad node -> new IrInstruction.ArrayLoad(values.apply(node.target()),
                    node.elementType(), values.apply(node.array()), values.apply(node.index()));
            case IrInstruction.ArrayStore node -> new IrInstruction.ArrayStore(node.elementType(),
                    values.apply(node.array()), values.apply(node.index()), values.apply(node.value()));
            case IrInstruction.BoundsCheck node -> new IrInstruction.BoundsCheck(values.apply(node.index()), node.length());
            default -> mapWide(instruction, values);
        };
    }

    /** The {@code long}/{@code double}/{@code float} instructions, split out to keep each switch readable. */
    private static IrInstruction mapWide(IrInstruction instruction, UnaryOperator<Value> values) {
        return switch (instruction) {
            case IrInstruction.LongConst node -> new IrInstruction.LongConst(values.apply(node.targetLow()),
                    values.apply(node.targetHigh()), node.value());
            case IrInstruction.LongBinary node -> new IrInstruction.LongBinary(values.apply(node.targetLow()),
                    values.apply(node.targetHigh()), node.operation(), values.apply(node.leftLow()),
                    values.apply(node.leftHigh()), values.apply(node.rightLow()), values.apply(node.rightHigh()));
            case IrInstruction.LongShift node -> new IrInstruction.LongShift(values.apply(node.targetLow()),
                    values.apply(node.targetHigh()), node.operation(), values.apply(node.valueLow()),
                    values.apply(node.valueHigh()), values.apply(node.shiftAmount()));
            case IrInstruction.LongNegate node -> new IrInstruction.LongNegate(values.apply(node.targetLow()),
                    values.apply(node.targetHigh()), values.apply(node.valueLow()), values.apply(node.valueHigh()));
            case IrInstruction.LongCompare node -> new IrInstruction.LongCompare(values.apply(node.target()),
                    values.apply(node.leftLow()), values.apply(node.leftHigh()), values.apply(node.rightLow()),
                    values.apply(node.rightHigh()));
            case IrInstruction.IntToLong node -> new IrInstruction.IntToLong(values.apply(node.targetLow()),
                    values.apply(node.targetHigh()), values.apply(node.value()));
            case IrInstruction.LongToInt node -> new IrInstruction.LongToInt(values.apply(node.target()),
                    values.apply(node.valueLow()), values.apply(node.valueHigh()));
            case IrInstruction.FloatBinary node -> new IrInstruction.FloatBinary(values.apply(node.target()),
                    node.operation(), values.apply(node.left()), values.apply(node.right()));
            case IrInstruction.FloatNegate node ->
                    new IrInstruction.FloatNegate(values.apply(node.target()), values.apply(node.value()));
            case IrInstruction.FloatCompare node -> new IrInstruction.FloatCompare(values.apply(node.target()),
                    values.apply(node.left()), values.apply(node.right()), node.nanResult());
            case IrInstruction.IntToFloat node ->
                    new IrInstruction.IntToFloat(values.apply(node.target()), values.apply(node.value()));
            case IrInstruction.FloatToInt node ->
                    new IrInstruction.FloatToInt(values.apply(node.target()), values.apply(node.value()));
            case IrInstruction.DoubleBinary node -> new IrInstruction.DoubleBinary(values.apply(node.target()),
                    node.operation(), values.apply(node.left()), values.apply(node.right()));
            case IrInstruction.DoubleNegate node ->
                    new IrInstruction.DoubleNegate(values.apply(node.target()), values.apply(node.value()));
            case IrInstruction.DoubleCompare node -> new IrInstruction.DoubleCompare(values.apply(node.target()),
                    values.apply(node.left()), values.apply(node.right()), node.nanResult());
            case IrInstruction.IntToDouble node ->
                    new IrInstruction.IntToDouble(values.apply(node.target()), values.apply(node.value()));
            case IrInstruction.DoubleToInt node ->
                    new IrInstruction.DoubleToInt(values.apply(node.target()), values.apply(node.value()));
            case IrInstruction.FloatToDouble node ->
                    new IrInstruction.FloatToDouble(values.apply(node.target()), values.apply(node.value()));
            case IrInstruction.DoubleToFloat node ->
                    new IrInstruction.DoubleToFloat(values.apply(node.target()), values.apply(node.value()));
            case IrInstruction.LongToDouble node -> new IrInstruction.LongToDouble(values.apply(node.target()),
                    values.apply(node.valueLow()), values.apply(node.valueHigh()));
            case IrInstruction.DoubleToLong node -> new IrInstruction.DoubleToLong(values.apply(node.targetLow()),
                    values.apply(node.targetHigh()), values.apply(node.value()));
            case IrInstruction.PackLong node -> new IrInstruction.PackLong(values.apply(node.target()),
                    values.apply(node.valueLow()), values.apply(node.valueHigh()));
            case IrInstruction.UnpackLong node -> new IrInstruction.UnpackLong(values.apply(node.targetLow()),
                    values.apply(node.targetHigh()), values.apply(node.value()));
            case IrInstruction.LongToFloat node -> new IrInstruction.LongToFloat(values.apply(node.target()),
                    values.apply(node.valueLow()), values.apply(node.valueHigh()));
            case IrInstruction.FloatToLong node -> new IrInstruction.FloatToLong(values.apply(node.targetLow()),
                    values.apply(node.targetHigh()), values.apply(node.value()));
            default -> throw new IllegalStateException("unmapped IR instruction " + instruction);
        };
    }

    public static IrTerminator map(IrTerminator terminator, UnaryOperator<Value> values, IntUnaryOperator blocks) {
        return switch (terminator) {
            case IrTerminator.Jump jump -> new IrTerminator.Jump(blocks.applyAsInt(jump.target()));
            case IrTerminator.Branch branch -> new IrTerminator.Branch(values.apply(branch.condition()),
                    blocks.applyAsInt(branch.trueTarget()), blocks.applyAsInt(branch.falseTarget()));
            case IrTerminator.Return returned -> new IrTerminator.Return(optional(returned.value(), values));
            case IrTerminator.Switch switched -> new IrTerminator.Switch(values.apply(switched.selector()),
                    switched.keys(), switched.targets().stream().map(blocks::applyAsInt).toList(),
                    blocks.applyAsInt(switched.defaultTarget()));
        };
    }

    private static List<Value> all(List<Value> values, UnaryOperator<Value> mapping) {
        return values.stream().map(mapping).toList();
    }

    private static Optional<Value> optional(Optional<Value> value, UnaryOperator<Value> mapping) {
        return value.map(mapping);
    }
}
