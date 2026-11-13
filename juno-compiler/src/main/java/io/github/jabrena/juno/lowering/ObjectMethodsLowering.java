package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.ObjectMethodsResolver;
import io.github.jabrena.juno.linker.ObjectMethodsSite;
import io.github.jabrena.juno.linker.ObjectMethodsSite.Component;
import io.github.jabrena.juno.linker.StringConcatSite;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Lowers the three record methods materialized by {@code java.lang.runtime.ObjectMethods.bootstrap}. */
final class ObjectMethodsLowering {
    private static final String STRING = "Ljava/lang/String;";

    private final MethodContext context;
    private final CallLowering calls;
    private final StringConcatLowering concatenations;
    private final ObjectMethodsResolver resolver;

    ObjectMethodsLowering(MethodContext context, CallLowering calls, StringConcatLowering concatenations) {
        this.context = context;
        this.calls = calls;
        this.concatenations = concatenations;
        this.resolver = context.program().objectMethodsResolver();
    }

    void lower(Instruction instruction) {
        ObjectMethodsSite site = resolver.resolve(context.method(), instruction, context.classes());
        String descriptor = context.constantPool().invokeDynamic(instruction.operandA()).descriptor();
        CallArguments arguments = calls.popArguments(instruction,
                new MethodRef("java/lang/runtime/ObjectMethods", site.operation().methodName(), descriptor),
                Descriptor.parse(descriptor), Optional.empty());
        Value receiver = arguments.value(0);
        switch (site.operation()) {
            case TO_STRING -> context.stack().push(toString(site, receiver));
            case HASH_CODE -> context.stack().push(hashCode(site, receiver));
            case EQUALS -> equals(site, receiver, arguments.value(1), instruction);
        }
    }

    private Value toString(ObjectMethodsSite site, Value receiver) {
        List<StringConcatSite.Part> parts = new ArrayList<>();
        List<Value> values = new ArrayList<>();
        parts.add(new StringConcatSite.LiteralPart(simpleName(site.className()) + "["));
        for (int index = 0; index < site.components().size(); index++) {
            Component component = site.components().get(index);
            parts.add(new StringConcatSite.LiteralPart(component.name() + "="));
            Value value = load(component, receiver);
            String type = component.descriptor();
            if (isRecord(component.descriptor())) {
                Value text = context.values().int32();
                context.emit(new IrInstruction.Call(Optional.of(text),
                        new MethodRef(referenceName(component.descriptor()), "toString", "()Ljava/lang/String;"),
                        List.of(value)));
                value = text;
                type = STRING;
            }
            values.add(value);
            parts.add(new StringConcatSite.ArgumentPart(index, type));
            if (index + 1 < site.components().size()) {
                parts.add(new StringConcatSite.LiteralPart(", "));
            }
        }
        parts.add(new StringConcatSite.LiteralPart("]"));
        return concatenations.emitConcat(parts, values);
    }

    private Value hashCode(ObjectMethodsSite site, Value receiver) {
        Value result = context.values().int32();
        context.emit(new IrInstruction.Const(result, 0));
        for (Component component : site.components()) {
            Value componentValue = load(component, receiver);
            Value componentHash = hash(component.descriptor(), componentValue);
            result = multiplyAndAdd(result, componentHash);
        }
        return result;
    }

    private void equals(ObjectMethodsSite site, Value receiver, Value other, Instruction instruction) {
        Integer typeId = context.program().objectTypeIds().get(site.className());
        if (typeId == null) {
            throw new CompileException(context.at(instruction) + ": no runtime type id for record "
                    + site.className().replace('/', '.'));
        }
        Value isRecord = context.values().int32();
        context.emit(new IrInstruction.InstanceOf(isRecord, other, typeId));
        int continuation = IrBlockWriter.SYNTHETIC_BLOCK_BASE + instruction.offset() * 4;
        int matching = continuation + 1;
        int nonMatching = continuation + 2;
        context.code().split(new IrTerminator.Branch(isRecord, matching, nonMatching), continuation);

        context.code().startBlock(matching);
        Value result = compareComponents(site, receiver, other);
        context.emit(new IrInstruction.StoreLocal(context.stack().bottomSlot(), result));
        context.code().finishBlock(() -> new IrTerminator.Jump(continuation));

        context.code().startBlock(nonMatching);
        Value falseValue = context.values().int32();
        context.emit(new IrInstruction.Const(falseValue, 0));
        context.emit(new IrInstruction.StoreLocal(context.stack().bottomSlot(), falseValue));
        context.code().finishBlock(() -> new IrTerminator.Jump(continuation));

        context.code().startBlock(continuation);
        Value resultOnStack = context.values().int32();
        context.emit(new IrInstruction.LoadLocal(resultOnStack, context.stack().bottomSlot()));
        context.stack().push(resultOnStack);
    }

    private Value compareComponents(ObjectMethodsSite site, Value receiver, Value other) {
        Value equal = context.values().int32();
        context.emit(new IrInstruction.Const(equal, 1));
        for (Component component : site.components()) {
            Value left = load(component, receiver);
            Value right = load(component, other);
            Value componentEqual = equal(component.descriptor(), left, right);
            Value combined = context.values().int32();
            context.emit(new IrInstruction.Binary(combined, BinaryOp.AND, equal, componentEqual));
            equal = combined;
        }
        return equal;
    }

    private Value equal(String descriptor, Value left, Value right) {
        if (Descriptor.isString(descriptor)) {
            Value result = context.values().int32();
            context.emit(new IrInstruction.IntrinsicCall(Optional.of(result), Intrinsic.STRING_EQUALS_NULL_SAFE,
                    Optional.of(left), List.of(right), List.of()));
            return result;
        }
        if (isRecord(descriptor)) {
            Value result = context.values().int32();
            context.emit(new IrInstruction.Call(Optional.of(result),
                    new MethodRef(referenceName(descriptor), "equals", "(Ljava/lang/Object;)Z"),
                    List.of(left, right)));
            return result;
        }
        if (Descriptor.isLong(descriptor)) {
            OperandStack.LongHalves leftHalves = unpack(left);
            OperandStack.LongHalves rightHalves = unpack(right);
            Value order = context.values().int32();
            context.emit(new IrInstruction.LongCompare(order, leftHalves.low(), leftHalves.high(),
                    rightHalves.low(), rightHalves.high()));
            return compareWithZero(order);
        }
        if (Descriptor.isFloat(descriptor)) {
            return compareWithZero(intrinsic(Intrinsic.FLOAT_COMPARE, List.of(left, right)));
        }
        if (Descriptor.isDouble(descriptor)) {
            return compareWithZero(intrinsic(Intrinsic.DOUBLE_COMPARE, List.of(left, right)));
        }
        Value result = context.values().int32();
        context.emit(new IrInstruction.Compare(result, Condition.EQUAL, left, right));
        return result;
    }

    private Value hash(String descriptor, Value value) {
        if (Descriptor.isString(descriptor)) {
            return intrinsic(Intrinsic.STRING_HASH_CODE, List.of(value));
        }
        if (Descriptor.isFloat(descriptor)) {
            return intrinsic(Intrinsic.FLOAT_HASH_CODE, List.of(value));
        }
        if (Descriptor.isDouble(descriptor)) {
            return intrinsic(Intrinsic.DOUBLE_HASH_CODE, List.of(value));
        }
        if (Descriptor.isLong(descriptor)) {
            OperandStack.LongHalves halves = unpack(value);
            Value result = context.values().int32();
            context.emit(new IrInstruction.Binary(result, BinaryOp.XOR, halves.low(), halves.high()));
            return result;
        }
        if (descriptor.equals("Z")) {
            Value scaled = context.values().int32();
            Value six = context.values().int32();
            context.emit(new IrInstruction.Const(six, -6));
            context.emit(new IrInstruction.Binary(scaled, BinaryOp.MULTIPLY, value, six));
            Value result = context.values().int32();
            Value base = context.values().int32();
            context.emit(new IrInstruction.Const(base, 1237));
            context.emit(new IrInstruction.Binary(result, BinaryOp.ADD, base, scaled));
            return result;
        }
        return value;
    }

    private Value multiplyAndAdd(Value result, Value componentHash) {
        Value multiplied = context.values().int32();
        Value multiplier = context.values().int32();
        context.emit(new IrInstruction.Const(multiplier, 31));
        context.emit(new IrInstruction.Binary(multiplied, BinaryOp.MULTIPLY, result, multiplier));
        Value next = context.values().int32();
        context.emit(new IrInstruction.Binary(next, BinaryOp.ADD, multiplied, componentHash));
        return next;
    }

    private Value compareWithZero(Value value) {
        Value zero = context.values().int32();
        context.emit(new IrInstruction.Const(zero, 0));
        Value result = context.values().int32();
        context.emit(new IrInstruction.Compare(result, Condition.EQUAL, value, zero));
        return result;
    }

    private Value intrinsic(Intrinsic intrinsic, List<Value> arguments) {
        Value result = context.values().int32();
        context.emit(new IrInstruction.IntrinsicCall(Optional.of(result), intrinsic, Optional.empty(), arguments,
                List.of()));
        return result;
    }

    private Value load(Component component, Value receiver) {
        Value target = context.values().of(typeOf(component.descriptor()));
        context.emit(new IrInstruction.LoadField(target, component.field(), receiver));
        return target;
    }

    private OperandStack.LongHalves unpack(Value value) {
        Value low = context.values().int32();
        Value high = context.values().int32();
        context.emit(new IrInstruction.UnpackLong(low, high, value));
        return new OperandStack.LongHalves(low, high);
    }

    private JunoType typeOf(String descriptor) {
        return Descriptor.isLong(descriptor) ? JunoType.INT64
                : Descriptor.isDouble(descriptor) ? JunoType.FLOAT64
                : Descriptor.isFloat(descriptor) ? JunoType.FLOAT32 : JunoType.INT32;
    }

    private boolean isRecord(String descriptor) {
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
            return false;
        }
        var type = context.classes().get(referenceName(descriptor));
        return type != null && type.isRecord();
    }

    private String referenceName(String descriptor) {
        return descriptor.substring(1, descriptor.length() - 1);
    }

    private String simpleName(String className) {
        String binaryName = className.substring(className.lastIndexOf('/') + 1);
        return binaryName.substring(binaryName.lastIndexOf('$') + 1);
    }
}
