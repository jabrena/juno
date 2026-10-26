package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

/** The concrete, deliberately small {@code ReentrantLock} subset lowered by Juno. */
public final class LockSupport {
    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    public static final String REENTRANT_LOCK = "java/util/concurrent/locks/ReentrantLock";
    public static final String REENTRANT_LOCK_DESCRIPTOR = "L" + REENTRANT_LOCK + ";";
    public static final MethodRef CONSTRUCTOR = new MethodRef(REENTRANT_LOCK, "<init>", "()V");


    public boolean isReentrantLockClass(String className) {
        return REENTRANT_LOCK.equals(className);
    }

    public boolean isReentrantLockType(String descriptor) {
        return REENTRANT_LOCK_DESCRIPTOR.equals(descriptor);
    }

    public void validateCall(MethodRef method) {
        if (isReentrantLockClass(method.owner()) && !intrinsicRegistry.isIntrinsic(method)) {
            throw new CompileException("Juno's restricted ReentrantLock subset supports only new ReentrantLock(), "
                    + "lock(), tryLock(), and unlock(): " + method.displayName());
        }
    }

    public void validateMethod(JavaMethod method) {
        if (method.isSynchronized() && method.isStatic()) {
            throw new CompileException("Juno's restricted synchronization subset supports instance synchronized "
                    + "methods only: " + method.reference().displayName()
                    + "; use a synchronized (lock) block for a static method");
        }
    }
}
