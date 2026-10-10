package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.linker.Descriptor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Resolves an enum constant's ordinal and per-constant associated integer field values, split out of {@link BytecodeToIr}. */
final class EnumFieldSupport {
    private EnumFieldSupport() {
    }

    static @Nullable Integer resolveEnumOrdinal(FieldRef field, Map<String, JavaClass> classes) {
        if (field.owner().equals("java/util/concurrent/StructuredTaskScope$Subtask$State")) {
            return switch (field.name()) {
                case "UNAVAILABLE" -> 0;
                case "SUCCESS" -> 1;
                case "FAILED" -> 2;
                default -> null;
            };
        }
        JavaClass owner = classes.get(field.owner());
        int ordinal = owner == null || !owner.isEnum() ? -1 : owner.enumConstantNames().indexOf(field.name());
        return ordinal < 0 ? null : ordinal;
    }

    static @Nullable List<Integer> resolveEnumIntegerFieldValues(FieldRef field, Map<String, JavaClass> classes,
                                                        BytecodeDecoder decoder) {
        JavaClass enumClass = classes.get(field.owner());
        if (enumClass == null || !enumClass.isEnum()) {
            return null;
        }
        if (!Descriptor.isIntegerLike(field.descriptor())) {
            throw new CompileException("Enum associated values must use an integer-like primitive field: "
                    + field.displayName());
        }
        String constructorDescriptor = findEnumFieldConstructorDescriptor(enumClass, field, decoder);
        return extractEnumFieldValues(enumClass, field, constructorDescriptor, decoder);
    }

    /**
     * Finds the {@code <init>} overload that assigns {@code field} directly from its single integer
     * constructor argument, and returns that constructor's descriptor.
     */
    static String findEnumFieldConstructorDescriptor(JavaClass enumClass, FieldRef field, BytecodeDecoder decoder) {
        String constructorDescriptor = null;
        for (JavaMethod method : enumClass.methods()) {
            if (!method.name().equals("<init>")) {
                continue;
            }
            List<Instruction> constructor = decoder.decode(method);
            for (int index = 2; index < constructor.size(); index++) {
                Instruction store = constructor.get(index);
                if (store.opcode() != 181
                        || !enumClass.constantPool().fieldRef(store.operandA()).equals(field)) {
                    continue;
                }
                Instruction receiverLoad = constructor.get(index - 2);
                Instruction valueLoad = constructor.get(index - 1);
                Descriptor descriptor = Descriptor.parse(method.descriptor());
                if (receiverLoad.opcode() != 42
                        || integerLocalIndex(valueLoad) != 3
                        || !descriptor.parameters().equals(List.of("Ljava/lang/String;", "I", "I"))) {
                    throw new CompileException("Enum associated value must be assigned directly from its single "
                            + "integer constructor argument: " + field.displayName());
                }
                constructorDescriptor = method.descriptor();
            }
        }
        if (constructorDescriptor == null) {
            throw new CompileException("Cannot resolve enum constructor assignment for " + field.displayName());
        }
        return constructorDescriptor;
    }

    /** Scans {@code <clinit>} for each constant's construction and reads back its associated field value. */
    static List<Integer> extractEnumFieldValues(JavaClass enumClass, FieldRef field, String constructorDescriptor,
                                                BytecodeDecoder decoder) {
        JavaMethod initializer = enumClass.findMethod("<clinit>", "()V");
        if (initializer == null) {
            throw new CompileException("Enum has no initializer for associated values: " + field.displayName());
        }
        List<Instruction> bytecode = decoder.decode(initializer);
        List<String> constantNames = enumClass.enumConstantNames();
        Integer[] values = new Integer[constantNames.size()];
        for (int index = 2; index < bytecode.size(); index++) {
            Instruction store = bytecode.get(index);
            if (store.opcode() != 179) {
                continue;
            }
            FieldRef constant = enumClass.constantPool().fieldRef(store.operandA());
            int ordinal = constant.owner().equals(enumClass.name())
                    ? constantNames.indexOf(constant.name()) : -1;
            if (ordinal < 0) {
                continue;
            }
            Instruction constructorCall = bytecode.get(index - 1);
            MethodRef called = constructorCall.opcode() == 183
                    ? enumClass.constantPool().methodRef(constructorCall.operandA()) : null;
            if (called == null || !called.owner().equals(enumClass.name()) || !called.name().equals("<init>")
                    || !called.descriptor().equals(constructorDescriptor)) {
                throw new CompileException("Cannot resolve enum constant construction for " + constant.displayName());
            }
            Integer value = EnumSwitchSupport.literalValue(bytecode.get(index - 2), enumClass);
            if (value == null) {
                throw new CompileException("Enum associated value must be a compile-time integer literal: "
                        + constant.displayName());
            }
            values[ordinal] = value;
        }
        List<Integer> resolved = new ArrayList<>(values.length);
        for (int ordinal = 0; ordinal < values.length; ordinal++) {
            if (values[ordinal] == null) {
                throw new CompileException("Cannot resolve associated value for enum constant "
                        + enumClass.name().replace('/', '.') + "." + constantNames.get(ordinal));
            }
            resolved.add(values[ordinal]);
        }
        return List.copyOf(resolved);
    }

    static int integerLocalIndex(Instruction instruction) {
        return switch (instruction.opcode()) {
            case 21 -> instruction.operandA();
            case 26, 27, 28, 29 -> instruction.opcode() - 26;
            default -> -1;
        };
    }
}
