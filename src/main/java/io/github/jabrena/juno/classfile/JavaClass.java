package io.github.jabrena.juno.classfile;

import java.util.List;

public record JavaClass(String name, ConstantPool constantPool, List<JavaMethod> methods) {
    public JavaMethod findMethod(String methodName, String descriptor) {
        return methods.stream()
                .filter(method -> method.name().equals(methodName) && method.descriptor().equals(descriptor))
                .findFirst()
                .orElse(null);
    }
}
