package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.MethodRef;

/** Small JDK bytecode idioms Juno lowers without linking their JDK implementations. */
public final class SupportedJdkMethods {
    private SupportedJdkMethods() {
    }

    public static boolean isRequireNonNull(MethodRef called) {
        return called.owner().equals("java/util/Objects") && called.name().equals("requireNonNull")
                && called.descriptor().equals("(Ljava/lang/Object;)Ljava/lang/Object;");
    }
}
