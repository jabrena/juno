package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Set;

/**
 * The deliberately small {@code java.util.concurrent.atomic} subset lowered by Juno: {@code AtomicInteger},
 * {@code AtomicBoolean} and {@code AtomicLong}, each an arena cell the cooperative scheduler can never interrupt
 * halfway through an operation.
 */
public final class AtomicSupport {
    private static final String PACKAGE = "java/util/concurrent/atomic/";
    private static final Set<String> CLASSES = Set.of(
            PACKAGE + "AtomicInteger", PACKAGE + "AtomicBoolean", PACKAGE + "AtomicLong");

    private AtomicSupport() {
    }

    public static boolean isAtomicClass(String className) {
        return CLASSES.contains(className);
    }

    public static boolean isAtomicType(String descriptor) {
        return descriptor.length() > 2 && descriptor.charAt(0) == 'L' && descriptor.endsWith(";")
                && isAtomicClass(descriptor.substring(1, descriptor.length() - 1));
    }

    public static boolean isConstruction(MethodRef called) {
        return isAtomicClass(called.owner()) && called.name().equals("<init>");
    }

    public static void validateCall(MethodRef method) {
        if (method.owner().startsWith(PACKAGE) && !IntrinsicRegistry.isIntrinsic(method)) {
            throw new CompileException("Juno's restricted atomic subset supports only AtomicInteger, AtomicBoolean "
                    + "and AtomicLong with get, set, getAndSet, compareAndSet and, on the numeric ones, "
                    + "increment/decrement/add: " + method.displayName());
        }
    }
}
