package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.intrinsic.SocketMethods;

/** Closed-world plumbing for {@code Socket} and its stream handles. */
public final class SocketSupport {
    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    public boolean isSocketType(String descriptor) {
        return descriptor.equals("L" + SocketMethods.SOCKET + ";")
                || descriptor.equals("L" + SocketMethods.OUTPUT_STREAM + ";")
                || descriptor.equals("L" + SocketMethods.INPUT_STREAM + ";");
    }

    public boolean isSocketOwner(String owner) {
        return owner.equals(SocketMethods.SOCKET) || owner.equals(SocketMethods.OUTPUT_STREAM)
                || owner.equals(SocketMethods.INPUT_STREAM);
    }

    /** Whether {@code called} is a constructor this support lowers to a runtime handle. */
    public boolean isConstruction(MethodRef called) {
        return called.name().equals("<init>") && isSocketOwner(called.owner())
                && intrinsicRegistry.isIntrinsic(called);
    }

    public void validateCall(MethodRef called) {
        if (isSocketOwner(called.owner()) && !intrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno's java.net.Socket subset does not support: " + called.displayName());
        }
    }
}
