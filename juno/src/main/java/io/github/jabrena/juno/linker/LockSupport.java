package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

/** The concrete, deliberately small {@code ReentrantLock} subset lowered by Juno. */
public final class LockSupport {
    public static final String REENTRANT_LOCK = "java/util/concurrent/locks/ReentrantLock";
    public static final String REENTRANT_LOCK_DESCRIPTOR = "L" + REENTRANT_LOCK + ";";
    public static final MethodRef CONSTRUCTOR = new MethodRef(REENTRANT_LOCK, "<init>", "()V");

    private LockSupport() {
    }

    public static boolean isReentrantLockClass(String className) {
        return REENTRANT_LOCK.equals(className);
    }

    public static boolean isReentrantLockType(String descriptor) {
        return REENTRANT_LOCK_DESCRIPTOR.equals(descriptor);
    }

    public static void validateCall(MethodRef method) {
        if (isReentrantLockClass(method.owner()) && !IntrinsicRegistry.isIntrinsic(method)) {
            throw new CompileException("Juno's restricted ReentrantLock subset supports only new ReentrantLock(), "
                    + "lock(), tryLock(), and unlock(): " + method.displayName());
        }
    }

    public static void validateMethod(JavaMethod method) {
        if (method.isSynchronized()) {
            throw new CompileException("Juno's restricted synchronization subset does not support synchronized "
                    + "methods: " + method.reference().displayName() + "; use a synchronized (lock) block instead");
        }
    }
}
