package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.linker.LambdaSite;

/** One object type-id branch and its concrete method in a closed-world interface dispatch. */
public record InterfaceTarget(int typeId, MethodRef method, LambdaSite lambda) {
    public InterfaceTarget(int typeId, MethodRef method) {
        this(typeId, method, null);
    }

    public boolean isLambda() {
        return lambda != null;
    }
}
