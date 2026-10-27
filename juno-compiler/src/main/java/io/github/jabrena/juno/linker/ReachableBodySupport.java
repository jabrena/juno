package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Map;

/** Decides whether a reachable method has bytecode that Juno must link. */
final class ReachableBodySupport {

    private final IntrinsicRegistry intrinsicRegistry;
    private final SupportedJdkMethods supportedJdkMethods;
    private final ThrowableTypes throwableTypes;

    ReachableBodySupport(IntrinsicRegistry intrinsicRegistry, SupportedJdkMethods supportedJdkMethods,
            ThrowableTypes throwableTypes) {
        this.intrinsicRegistry = intrinsicRegistry;
        this.supportedJdkMethods = supportedJdkMethods;
        this.throwableTypes = throwableTypes;
    }

    /** Whether {@code called} is an ordinary method whose bytecode must be linked, not one Juno lowers itself. */
    boolean hasReachableBody(MethodRef called, Map<String, JavaClass> classes) {
        return !intrinsicRegistry.isIntrinsic(called)
                && !ClojureSupport.isLowered(called)
                && !isRuntimeBaseConstructor(called)
                && !throwableTypes.isBuiltInConstructor(called)
                && !throwableTypes.isGetMessage(called, classes)
                && !throwableTypes.isAddSuppressed(called, classes)
                && !isEnumOperation(classes, called)
                && !isCompileTimeGetenv(called)
                && !supportedJdkMethods.isRequireNonNull(called);
    }

    private boolean isCompileTimeGetenv(MethodRef called) {
        return called.owner().equals("java/lang/System") && called.name().equals("getenv")
                && called.descriptor().equals("(Ljava/lang/String;)Ljava/lang/String;");
    }

    private boolean isRuntimeBaseConstructor(MethodRef called) {
        return called.name().equals("<init>") && called.descriptor().endsWith(")V")
                && (called.owner().startsWith("java/lang/")
                        || called.owner().equals("java/lang/Enum"));
    }

    private boolean isEnumOperation(Map<String, JavaClass> classes, MethodRef called) {
        JavaClass owner = classes.get(called.owner());
        if (owner != null && owner.isEnum()) {
            return (called.name().equals("ordinal") && called.descriptor().equals("()I"))
                    || (called.name().equals("values") && called.descriptor().startsWith("()[L"));
        }
        return called.owner().equals("java/lang/Enum")
                && called.name().equals("ordinal") && called.descriptor().equals("()I");
    }
}
