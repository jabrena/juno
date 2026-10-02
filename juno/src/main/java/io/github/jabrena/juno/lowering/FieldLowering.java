package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.ThreadSupport;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.List;
import java.util.Map;

/** {@code getstatic}/{@code putstatic}/{@code getfield}/{@code putfield} opcode lowering, split out of {@link BytecodeToIr}. */
final class FieldLowering {
    private FieldLowering() {
    }

    static Lowered lowerStaticField(LinkedMethod linked, Instruction instruction, int opcode,
                                     List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                     ValueTracking tracking, Map<String, JavaClass> classes, BytecodeDecoder decoder) {
        switch (opcode) {

                    case 178 -> {
                        FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
                        Integer ordinal = EnumFieldSupport.resolveEnumOrdinal(field, classes);
                        if (ordinal != null) {
                            nextValueId = pushConst(instructions, stackBase, depth, nextValueId, ordinal, tracking);
                            depth++;
                        } else if (field.name().startsWith("$SwitchMap$")) {
                            List<Integer> mapping = EnumSwitchSupport.resolveEnumSwitchMap(field, classes, decoder);
                            Value target = Value.int32(nextValueId++);
                            instructions.add(new IrInstruction.IntArrayConst(target, mapping));
                            tracking.markKnownArray(target, mapping.size());
                            storeToStack(instructions, stackBase, depth, target, tracking);
                            depth++;
                        } else {
                            JunoType type = validateStaticField(linked, instruction, field, classes);
                            Value target = new Value(nextValueId++, type);
                            instructions.add(new IrInstruction.LoadStatic(target, field));
                            if (type == JunoType.INT64) {
                                Value low = Value.int32(nextValueId++);
                                Value high = Value.int32(nextValueId++);
                                instructions.add(new IrInstruction.UnpackLong(low, high, target));
                                storeWideToStack(instructions, stackBase, depth, low, high, tracking);
                            } else if (type == JunoType.FLOAT64) {
                                storeDoubleToStack(instructions, stackBase, depth, target, tracking);
                            } else {
                                storeToStack(instructions, stackBase, depth, target, tracking);
                            }
                            depth += type.jvmSlots();
                        }
                    }

                    case 179 -> {
                        FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
                        JunoType type = validateStaticField(linked, instruction, field, classes);
                        depth -= type.jvmSlots();
                        if (type == JunoType.INT64) {
                            WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
                            nextValueId = value.nextValueId();
                            Value packed = Value.int64(nextValueId++);
                            instructions.add(new IrInstruction.PackLong(packed, value.low(), value.high()));
                            instructions.add(new IrInstruction.StoreStatic(field, packed));
                        } else {
                            Popped value = switch (type) {
                                case INT32 -> pop(instructions, stackBase, depth, nextValueId, tracking);
                                case FLOAT32 -> popFloat(instructions, stackBase, depth, nextValueId, tracking);
                                case FLOAT64 -> popDouble(instructions, stackBase, depth, nextValueId, tracking);
                                case INT64 -> throw new IllegalStateException("handled above");
                            };
                            nextValueId = value.nextValueId();
                            instructions.add(new IrInstruction.StoreStatic(field, value.value()));
                        }
                    }
            default -> throw new IllegalStateException("unreachable static-field opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }

    static Lowered lowerFieldLoad(LinkedMethod linked, Instruction instruction,
                                   List<IrInstruction> instructions, int stackBase, int depth,
                                   int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes,
                                   BytecodeDecoder decoder) {
        FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
        JunoType type = validateInstanceField(linked, instruction, field, classes);
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        Value target = new Value(nextValueId++, type);
        List<Integer> enumFieldValues = EnumFieldSupport.resolveEnumIntegerFieldValues(field, classes, decoder);
        if (enumFieldValues != null) {
            Value values = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.IntArrayConst(values, enumFieldValues));
            instructions.add(new IrInstruction.BoundsCheck(receiver.value(), enumFieldValues.size()));
            instructions.add(new IrInstruction.ArrayLoad(target, ArrayElementType.INT, values, receiver.value()));
        } else {
            instructions.add(new IrInstruction.LoadField(target, field, receiver.value()));
        }
        if (type == JunoType.INT64) {
            Value low = Value.int32(nextValueId++);
            Value high = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.UnpackLong(low, high, target));
            storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        } else if (type == JunoType.FLOAT64) {
            storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        } else {
            storeToStack(instructions, stackBase, depth, target, tracking);
        }
        return new Lowered(nextValueId, depth + type.jvmSlots());
    }

    static Lowered lowerFieldStore(LinkedMethod linked, Instruction instruction,
                                    List<IrInstruction> instructions, int stackBase, int depth,
                                    int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes) {
        FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
        JunoType type = validateInstanceField(linked, instruction, field, classes);
        depth -= type.jvmSlots();
        Value stored;
        if (type == JunoType.INT64) {
            WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
            nextValueId = value.nextValueId();
            stored = Value.int64(nextValueId++);
            instructions.add(new IrInstruction.PackLong(stored, value.low(), value.high()));
        } else {
            Popped value = type == JunoType.FLOAT64
                    ? popDouble(instructions, stackBase, depth, nextValueId, tracking)
                    : type == JunoType.FLOAT32
                            ? popFloat(instructions, stackBase, depth, nextValueId, tracking)
                            : pop(instructions, stackBase, depth, nextValueId, tracking);
            nextValueId = value.nextValueId();
            stored = value.value();
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        instructions.add(new IrInstruction.StoreField(field, receiver.value(), stored));
        return new Lowered(nextValueId, depth);
    }

    static JunoType validateStaticField(LinkedMethod linked, Instruction instruction, FieldRef field,
                                         Map<String, JavaClass> classes) {
        JavaClass owner = classes.get(field.owner());
        FieldInfo declaration = owner == null ? null : owner.findField(field.name(), field.descriptor());
        String location = linked.method().reference().displayName() + " at bytecode offset " + instruction.offset();
        if (declaration == null || !declaration.isStatic()) {
            throw new CompileException(location + ": static field not found: " + field.displayName());
        }
        return typeOfDescriptor(location, field, classes);
    }

    static JunoType validateInstanceField(LinkedMethod linked, Instruction instruction, FieldRef field,
                                           Map<String, JavaClass> classes) {
        JavaClass owner = classes.get(field.owner());
        FieldInfo declaration = owner == null ? null : owner.findField(field.name(), field.descriptor());
        String location = linked.method().reference().displayName() + " at bytecode offset " + instruction.offset();
        if (declaration == null || declaration.isStatic()) {
            throw new CompileException(location + ": instance field not found: " + field.displayName());
        }
        return typeOfDescriptor(location, field, classes);
    }

    static JunoType typeOfDescriptor(String location, FieldRef field, Map<String, JavaClass> classes) {
        if (Descriptor.isIntegerLike(field.descriptor())) {
            return JunoType.INT32;
        }
        if (Descriptor.isLong(field.descriptor())) {
            return JunoType.INT64;
        }
        if (Descriptor.isFloat(field.descriptor())) {
            return JunoType.FLOAT32;
        }
        if (Descriptor.isDouble(field.descriptor())) {
            return JunoType.FLOAT64;
        }
        if (Descriptor.isString(field.descriptor())
                || ThreadSupport.isThreadType(field.descriptor())
                || Descriptor.isArrayType(field.descriptor())
                || Descriptor.isReferenceType(field.descriptor(), classes.keySet())) {
            return JunoType.INT32;
        }
        throw new CompileException(location + ": unsupported field type: " + field.displayName());
    }
}
