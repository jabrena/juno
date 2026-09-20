package io.github.jabrena.juno.classfile;

import java.util.List;
import java.util.Optional;

/**
 * {@code superClassName} is the internal name of the direct superclass, or {@code null} only for
 * {@code java/lang/Object} itself (constant-pool index 0, per the class file spec). {@code fields} lists
 * every non-inherited field in class-file declaration order.
 *
 * <p>An enum constant is a field flagged {@code ACC_ENUM}; {@link #enumConstantNames()} returns those
 * names in declaration order, which the JLS guarantees matches {@code Enum.ordinal()} assignment
 * regardless of custom constructors or per-constant class bodies (JLS 8.9.1: "the ordinal of an enum
 * constant is its position in its enum declaration, where the initial constant is assigned an ordinal of
 * zero"). Juno never constructs a real enum object; a constant is represented purely by this 0-based
 * index. A supported constructor-associated integer field is materialized as an immutable lookup table
 * indexed by that ordinal (see {@link io.github.jabrena.juno.lowering.BytecodeToIr}'s handling of enum
 * {@code getstatic} and {@code getfield} instructions).
 *
 * <p>A record class (superclass {@code java/lang/Record}) declares no instance fields beyond its
 * components (JLS 8.10), so every non-static field, in declaration order, is a record component; see
 * {@link #recordComponents()}.
 */
public record JavaClass(String name, int accessFlags, String superClassName, ConstantPool constantPool,
                         List<JavaMethod> methods, List<FieldInfo> fields, Optional<String> boardApiClassName) {
    private static final int ACC_ENUM = 0x4000;
    private static final int ACC_FINAL = 0x0010;
    private static final String RECORD_SUPERCLASS = "java/lang/Record";

    public boolean isEnum() {
        return (accessFlags & ACC_ENUM) != 0;
    }

    public boolean isRecord() {
        return RECORD_SUPERCLASS.equals(superClassName);
    }

    public boolean isFinal() {
        return (accessFlags & ACC_FINAL) != 0;
    }

    public List<String> enumConstantNames() {
        return fields.stream().filter(FieldInfo::isEnumConstant).map(FieldInfo::name).toList();
    }

    /** Every non-static field, in declaration order — meaningful only when {@link #isRecord()}. */
    public List<FieldInfo> recordComponents() {
        return fields.stream().filter(field -> !field.isStatic()).toList();
    }

    public JavaMethod findMethod(String methodName, String descriptor) {
        return methods.stream()
                .filter(method -> method.name().equals(methodName) && method.descriptor().equals(descriptor))
                .findFirst()
                .orElse(null);
    }

    public FieldInfo findField(String fieldName, String descriptor) {
        return fields.stream()
                .filter(field -> field.name().equals(fieldName) && field.descriptor().equals(descriptor))
                .findFirst()
                .orElse(null);
    }
}
