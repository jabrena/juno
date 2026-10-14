package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

/** Closed-world plumbing for UUID values represented by one-word handles to immutable arena blocks. */
public final class UuidSupport {
    public static final String UUID = "java/util/UUID";

    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    public boolean isUuidType(String descriptor) {
        return descriptor.equals("L" + UUID + ";");
    }

    public boolean isUuidOwner(String owner) {
        return owner.equals(UUID);
    }

    public boolean isConstruction(MethodRef called) {
        return called.name().equals("<init>") && isUuidOwner(called.owner())
                && intrinsicRegistry.isIntrinsic(called);
    }

    public void validateCall(MethodRef called) {
        if (isUuidOwner(called.owner()) && !intrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno's java.util.UUID subset does not support: " + called.displayName());
        }
    }
}
