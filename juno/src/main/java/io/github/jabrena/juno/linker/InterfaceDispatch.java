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

    /** The allocated receiver class and the method body selected for it. */
    public record Target(String className, MethodRef method) {
    }
}
