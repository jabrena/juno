package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Compact-record constructor and accessor opcode lowering, split out of {@link BytecodeToIr}. */
final class RecordLowering {
    private RecordLowering() {
    }

    static Lowered lowerRecordConstruction(LinkedMethod linked, Instruction instruction, MethodRef called,
                                             List<IrInstruction> instructions, int stackBase, int depth,
                                             int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes,
                                             BytecodeDecoder decoder, Map<String, List<FieldInfo>> validatedRecords) {
        if (!called.name().equals("<init>")) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": invokespecial is only supported for constructing a recognized "
                    + "record (" + called.displayName() + " is not a constructor call this can resolve)");
        }
        JavaClass recordClass = classes.get(called.owner());
        if (recordClass == null || !recordClass.isRecord()) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": object construction is only supported for simple records ("
                    + called.owner().replace('/', '.') + " is not one); general objects/constructors are "
                    + "not supported");
        }
        List<FieldInfo> components = validateSimpleRecord(recordClass, decoder, validatedRecords);
        Value[] fieldValues = new Value[components.size()];
        for (int index = fieldValues.length - 1; index >= 0; index--) {
            Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
            nextValueId = popped.nextValueId();
            fieldValues[index] = popped.value();
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        // depth now reflects "1 item left on the stack" (the dup'd copy that survives, per the class-level
        // docs above); its slot is stackBase + depth - 1, matching pop()'s own "depth after popping" convention.
        tracking.markStackSlotRecord(stackBase + depth - 1, new RecordInstance(recordClass.name(), List.of(fieldValues)));
        return new Lowered(nextValueId, depth);
    }

    static Lowered lowerRecordAccessor(LinkedMethod linked, Instruction instruction, MethodRef called,
                                         List<IrInstruction> instructions, int stackBase, int depth,
                                         int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes,
                                         BytecodeDecoder decoder, Map<String, List<FieldInfo>> validatedRecords) {
        JavaClass recordClass = Objects.requireNonNull(classes.get(called.owner()), called.owner());
        List<FieldInfo> components = validateSimpleRecord(recordClass, decoder, validatedRecords);
        int componentIndex = -1;
        for (int index = 0; index < components.size(); index++) {
            if (components.get(index).name().equals(called.name())) {
                componentIndex = index;
                break;
            }
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        RecordInstance instance = tracking.knownRecord(receiver.value());
        if (instance == null || !instance.className().equals(recordClass.name()) || componentIndex < 0) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": " + called.displayName() + " can only be called on a record "
                    + "constructed directly in this method and assigned to a local exactly once "
                    + "(\"effectively final\"); this receiver's construction site could not be resolved at "
                    + "compile time");
        }
        Value fieldValue = instance.fieldValues().get(componentIndex);
        storeToStack(instructions, stackBase, depth, fieldValue, tracking);
        depth++;
        return new Lowered(nextValueId, depth);
    }

    static List<FieldInfo> validateSimpleRecord(JavaClass recordClass, BytecodeDecoder decoder,
                                                Map<String, List<FieldInfo>> validatedRecords) {
        List<FieldInfo> cached = validatedRecords.get(recordClass.name());
        if (cached != null) {
            return cached;
        }
        List<FieldInfo> components = recordClass.recordComponents();
        for (FieldInfo component : components) {
            if (!Descriptor.isIntegerLike(component.descriptor())) {
                throw new CompileException("Record " + recordClass.name().replace('/', '.') + " has a component '"
                        + component.name() + "' of unsupported type " + component.descriptor() + "; Juno's "
                        + "record support is limited to boolean/byte/char/short/int components");
            }
        }
        String initDescriptor = "(" + components.stream().map(FieldInfo::descriptor).collect(Collectors.joining()) + ")V";
        JavaMethod init = recordClass.findMethod("<init>", initDescriptor);
        if (init == null || init.code() == null) {
            throw new CompileException("Record " + recordClass.name().replace('/', '.')
                    + " has no matching canonical constructor");
        }
        validateCanonicalConstructor(recordClass, init, components, decoder);
        for (FieldInfo component : components) {
            String accessorDescriptor = "()" + component.descriptor();
            JavaMethod accessor = recordClass.findMethod(component.name(), accessorDescriptor);
            if (accessor == null || accessor.code() == null) {
                throw new CompileException("Record " + recordClass.name().replace('/', '.') + " has no accessor "
                        + "method for component '" + component.name() + "'");
            }
            validateTrivialAccessor(recordClass, accessor, component, decoder);
        }
        validatedRecords.put(recordClass.name(), components);
        return components;
    }

    static void validateCanonicalConstructor(JavaClass recordClass, JavaMethod init, List<FieldInfo> components,
                                             BytecodeDecoder decoder) {
        List<Instruction> instructions = decoder.decode(init);
        int expectedCount = 2 + 3 * components.size() + 1;
        if (instructions.size() != expectedCount
                || instructions.get(0).opcode() != 42
                || instructions.get(1).opcode() != 183
                || instructions.get(instructions.size() - 1).opcode() != 177) {
            throw unsupportedConstructor(recordClass);
        }
        for (int index = 0; index < components.size(); index++) {
            Instruction loadThis = instructions.get(2 + 3 * index);
            Instruction loadArg = instructions.get(2 + 3 * index + 1);
            Instruction store = instructions.get(2 + 3 * index + 2);
            Integer argSlot = LocalSlotAnalysis.intLoadSlot(loadArg);
            if (loadThis.opcode() != 42 || store.opcode() != 181 || argSlot == null || argSlot != index + 1) {
                throw unsupportedConstructor(recordClass);
            }
            FieldRef field = recordClass.constantPool().fieldRef(store.operandA());
            if (!field.owner().equals(recordClass.name()) || !field.name().equals(components.get(index).name())) {
                throw unsupportedConstructor(recordClass);
            }
        }
    }

    static void validateTrivialAccessor(JavaClass recordClass, JavaMethod accessor, FieldInfo component,
                                        BytecodeDecoder decoder) {
        List<Instruction> instructions = decoder.decode(accessor);
        FieldRef field = instructions.size() == 3 && instructions.get(1).opcode() == 180
                ? recordClass.constantPool().fieldRef(instructions.get(1).operandA()) : null;
        if (instructions.size() != 3
                || instructions.get(0).opcode() != 42
                || instructions.get(1).opcode() != 180
                || instructions.get(2).opcode() != 172
                || field == null
                || !field.owner().equals(recordClass.name())
                || !field.name().equals(component.name())) {
            throw new CompileException("Record " + recordClass.name().replace('/', '.') + "." + component.name()
                    + "() has a custom body; Juno's record support requires the plain compiler-generated "
                    + "accessor (just returning the field)");
        }
    }

    static CompileException unsupportedConstructor(JavaClass recordClass) {
        return new CompileException("Record " + recordClass.name().replace('/', '.') + " has a custom or compact "
                + "constructor body; Juno's record support requires the plain compiler-generated canonical "
                + "constructor (no extra validation/transformation logic)");
    }
}
