package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Set;

/**
 * The deliberately small {@code java.util.concurrent} subset lowered by Juno beside virtual threads: a
 * {@code CountDownLatch} and a {@code Semaphore}, each an arena counter that the scheduler's waits poll.
 */
public final class ConcurrencySupport {
    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    private static final String PACKAGE = "java/util/concurrent/";
    private static final Set<String> CLASSES = Set.of(PACKAGE + "CountDownLatch", PACKAGE + "Semaphore",
            PACKAGE + "CyclicBarrier", "java/util/concurrent/locks/Condition", "java/util/concurrent/locks/Lock",
            "java/util/concurrent/locks/ReadWriteLock", "java/util/concurrent/locks/ReentrantReadWriteLock",
            "java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock",
            "java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock");


    public boolean isConcurrencyClass(String className) {
        return CLASSES.contains(className);
    }

    public boolean isConcurrencyType(String descriptor) {
        return descriptor.length() > 2 && descriptor.charAt(0) == 'L' && descriptor.endsWith(";")
                && isConcurrencyClass(descriptor.substring(1, descriptor.length() - 1));
    }

    public boolean isConstruction(MethodRef called) {
        return isConcurrencyClass(called.owner()) && called.name().equals("<init>");
    }

    public void validateCall(MethodRef method) {
        if (isConcurrencyClass(method.owner()) && !intrinsicRegistry.isIntrinsic(method)) {
            throw new CompileException("Juno's restricted concurrency subset supports CountDownLatch(int) with "
                    + "countDown, getCount and await, and Semaphore(int) with acquire, release, tryAcquire and "
                    + "availablePermits, CyclicBarrier(int) with await and getParties, and Condition with await, "
                    + "signal and signalAll (from ReentrantLock.newCondition), and ReentrantReadWriteLock with "
                    + "readLock and writeLock, whose Lock supports lock, tryLock and unlock: "
                    + method.displayName());
        }
    }
}
