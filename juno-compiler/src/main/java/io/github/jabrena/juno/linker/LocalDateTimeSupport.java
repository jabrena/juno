package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

/** Closed-world plumbing for immutable {@code LocalDateTime} values represented by one-word handles. */
public final class LocalDateTimeSupport {
    public static final String LOCAL_DATE_TIME = "java/time/LocalDateTime";

    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    public boolean isLocalDateTimeType(String descriptor) {
        return descriptor.equals("L" + LOCAL_DATE_TIME + ";");
    }

    public boolean isMinimum(FieldRef field) {
        return field.owner().equals(LOCAL_DATE_TIME)
                && field.name().equals("MIN")
                && isLocalDateTimeType(field.descriptor());
    }

    public void validateCall(MethodRef called) {
        if (called.owner().equals(LOCAL_DATE_TIME) && !intrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno's java.time.LocalDateTime subset does not support: "
                    + called.displayName());
        }
    }
}
