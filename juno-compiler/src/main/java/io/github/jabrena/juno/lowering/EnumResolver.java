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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.github.jabrena.juno.bytecode.Opcodes.*;

/**
 * Resolves enums at compile time. Juno never constructs an enum object: a constant is its ordinal, an associated
 * integer field becomes a lookup table indexed by ordinal, and the synthetic {@code $SwitchMap$} array javac builds
 * for a {@code switch} over an enum becomes a constant table.
 */
final class EnumResolver {
    private static final String SUBTASK_STATE = "java/util/concurrent/StructuredTaskScope$Subtask$State";

    private final Map<String, JavaClass> classes;
    private final BytecodeDecoder decoder;

    EnumResolver(Map<String, JavaClass> classes, BytecodeDecoder decoder) {
        this.classes = classes;
        this.decoder = decoder;
    }

    /** The ordinal of the enum constant {@code field} names, or {@code null} when it is not an enum constant. */
    @Nullable Integer ordinal(FieldRef field) {
        if (field.owner().equals(SUBTASK_STATE)) {
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

    /**
     * The value of an enum's integer field for each constant, by ordinal, or {@code null} when {@code field} is not
     * an enum's. javac compiles {@code KEY(123)} into a literal constructor argument in {@code <clinit>} and a
     * direct parameter-to-field assignment in the constructor, which is the only shape this resolves.
     */
    @Nullable List<Integer> integerFieldValues(FieldRef field) {
        JavaClass enumClass = classes.get(field.owner());
        if (enumClass == null || !enumClass.isEnum()) {
            return null;
        }
        if (!Descriptor.isIntegerLike(field.descriptor())) {
            throw new CompileException("Enum associated values must use an integer-like primitive field: "
                    + field.displayName());
        }
        String constructorDescriptor = assigningConstructor(enumClass, field);
        return fieldValuesByOrdinal(enumClass, field, constructorDescriptor);
    }

    /** The per-ordinal values of the synthetic {@code $SwitchMap$} array {@code requested}. */
    List<Integer> switchMap(FieldRef requested) {
        JavaClass mappingClass = classes.get(requested.owner());
        JavaMethod initializer = mappingClass == null ? null : mappingClass.findMethod("<clinit>", "()V");
        if (mappingClass == null || initializer == null) {
            throw new CompileException("Synthetic enum switch map has no initializer: " + requested.displayName());
        }
        IntLiterals literals = new IntLiterals(mappingClass.constantPool());
        List<Instruction> bytecode = decoder.decode(initializer);
        List<Integer> mapping = null;
        for (int index = 4; index < bytecode.size(); index++) {
            if (bytecode.get(index).opcode() != IASTORE) {
                continue;
            }
            Instruction mapLoad = bytecode.get(index - 4);
            Instruction enumLoad = bytecode.get(index - 3);
            Instruction ordinalCall = bytecode.get(index - 2);
            if (mapLoad.opcode() != GETSTATIC || enumLoad.opcode() != GETSTATIC
                    || ordinalCall.opcode() != INVOKEVIRTUAL
                    || !mappingClass.constantPool().fieldRef(mapLoad.operandA()).equals(requested)) {
                continue;
            }
            FieldRef enumField = mappingClass.constantPool().fieldRef(enumLoad.operandA());
            JavaClass enumClass = classes.get(enumField.owner());
            int ordinal = enumClass == null ? -1 : enumClass.enumConstantNames().indexOf(enumField.name());
            Integer switchValue = literals.pushedBy(bytecode.get(index - 1));
            if (enumClass == null || ordinal < 0 || switchValue == null) {
                throw new CompileException("Cannot resolve synthetic enum switch entry for " + requested.displayName());
            }
            if (mapping == null) {
                mapping = new ArrayList<>(Collections.nCopies(enumClass.enumConstantNames().size(), 0));
            }
            mapping.set(ordinal, switchValue);
        }
        if (mapping == null) {
            throw new CompileException("Cannot resolve synthetic enum switch map: " + requested.displayName());
        }
        return List.copyOf(mapping);
    }

    /** The descriptor of the {@code <init>} that assigns {@code field} directly from its single int argument. */
    private String assigningConstructor(JavaClass enumClass, FieldRef field) {
        String constructorDescriptor = null;
        for (JavaMethod method : enumClass.methods()) {
            if (!method.name().equals("<init>")) {
                continue;
            }
            List<Instruction> constructor = decoder.decode(method);
            for (int index = 2; index < constructor.size(); index++) {
                Instruction store = constructor.get(index);
                if (store.opcode() != PUTFIELD || !enumClass.constantPool().fieldRef(store.operandA()).equals(field)) {
                    continue;
                }
                Descriptor descriptor = Descriptor.parse(method.descriptor());
                if (constructor.get(index - 2).opcode() != ALOAD_0
                        || intLocal(constructor.get(index - 1)) != 3
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
    private List<Integer> fieldValuesByOrdinal(JavaClass enumClass, FieldRef field, String constructorDescriptor) {
        JavaMethod initializer = enumClass.findMethod("<clinit>", "()V");
        if (initializer == null) {
            throw new CompileException("Enum has no initializer for associated values: " + field.displayName());
        }
        IntLiterals literals = new IntLiterals(enumClass.constantPool());
        List<Instruction> bytecode = decoder.decode(initializer);
        List<String> constantNames = enumClass.enumConstantNames();
        Integer[] values = new Integer[constantNames.size()];
        for (int index = 2; index < bytecode.size(); index++) {
            Instruction store = bytecode.get(index);
            if (store.opcode() != PUTSTATIC) {
                continue;
            }
            FieldRef constant = enumClass.constantPool().fieldRef(store.operandA());
            int ordinal = constant.owner().equals(enumClass.name()) ? constantNames.indexOf(constant.name()) : -1;
            if (ordinal < 0) {
                continue;
            }
            Instruction constructorCall = bytecode.get(index - 1);
            MethodRef called = constructorCall.opcode() == INVOKESPECIAL
                    ? enumClass.constantPool().methodRef(constructorCall.operandA()) : null;
            if (called == null || !called.owner().equals(enumClass.name()) || !called.name().equals("<init>")
                    || !called.descriptor().equals(constructorDescriptor)) {
                throw new CompileException("Cannot resolve enum constant construction for " + constant.displayName());
            }
            Integer value = literals.pushedBy(bytecode.get(index - 2));
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

    /** The local an {@code iload}/{@code iload_N} reads, or {@code -1} for any other instruction. */
    private int intLocal(Instruction instruction) {
        int opcode = instruction.opcode();
        if (opcode == ILOAD) {
            return instruction.operandA();
        }
        return opcode >= ILOAD_0 && opcode <= ILOAD_3 ? opcode - ILOAD_0 : -1;
    }
}
