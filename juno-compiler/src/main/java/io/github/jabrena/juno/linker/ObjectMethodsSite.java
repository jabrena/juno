package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.FieldRef;

import java.util.List;

/** A validated {@code java.lang.runtime.ObjectMethods.bootstrap} record-object-method call site. */
public record ObjectMethodsSite(String className, Operation operation, List<Component> components) {
    public ObjectMethodsSite {
        components = List.copyOf(components);
    }

    public boolean requiresTypeId() {
        return operation == Operation.EQUALS;
    }

    public enum Operation {
        TO_STRING("toString", "()Ljava/lang/String;"),
        HASH_CODE("hashCode", "()I"),
        EQUALS("equals", "(Ljava/lang/Object;)Z");

        private final String methodName;
        private final String descriptor;

        Operation(String methodName, String descriptor) {
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        public String methodName() {
            return methodName;
        }

        public String descriptor() {
            return descriptor;
        }
    }

    public record Component(String name, String descriptor, FieldRef field) {
    }
}
