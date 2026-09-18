package io.github.jabrena.juno.classfile;

import java.util.List;

/**
 * {@code enumConstantNames} holds the names of every field flagged {@code ACC_ENUM}, in class-file
 * declaration order — which the JLS guarantees matches {@code Enum.ordinal()} assignment, regardless of
 * custom constructors or per-constant class bodies (JLS 8.9.1: "the ordinal of an enum constant is its
 * position in its enum declaration, where the initial constant is assigned an ordinal of zero"). Juno
 * never constructs a real enum object; a constant is represented purely by this 0-based index (see
 * {@link io.github.jabrena.juno.lowering.BytecodeToIr}'s handling of {@code getstatic} on an enum field).
 * Empty for a non-enum class.
 */
public record JavaClass(String name, int accessFlags, ConstantPool constantPool, List<JavaMethod> methods,
                         List<String> enumConstantNames) {
    private static final int ACC_ENUM = 0x4000;

    public boolean isEnum() {
        return (accessFlags & ACC_ENUM) != 0;
    }

    public JavaMethod findMethod(String methodName, String descriptor) {
        return methods.stream()
                .filter(method -> method.name().equals(methodName) && method.descriptor().equals(descriptor))
                .findFirst()
                .orElse(null);
    }
}
