package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;

/** Closed-world concrete targets of one reachable {@code invokeinterface} instruction. */
public record InterfaceDispatch(MethodRef interfaceMethod, List<Target> targets) {
    public InterfaceDispatch {
        targets = List.copyOf(targets);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("An interface dispatch must have at least one target");
        }
    }

    /** The receiver type and either its concrete method body or its compiler-generated lambda adapter. */
    public record Target(String className, MethodRef method, LambdaSite lambda) {
        public Target(String className, MethodRef method) {
            this(className, method, null);
        }

        public static Target lambda(LambdaSite site) {
            return new Target(site.syntheticClassName(), site.implementation().method(), site);
        }

        public boolean isLambda() {
            return lambda != null;
        }
    }
}
