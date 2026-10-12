package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Map;


/**
 * The closed-world plumbing behind {@code java.lang.Thread}: a started thread runs its {@code Runnable} on a
 * stack of its own, entered from the runtime shim, so the linker registers one synthetic
 * {@code Runnable.run()} call site that resolves against every reachable {@code Runnable} (classes and lambdas)
 * and the lowering turns it into the generated {@link #ENTRY_METHOD thread entry} function.
 */
public final class ThreadSupport {
    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    private final ScopedValueSupport scopedValueSupport = new ScopedValueSupport();

    private final StructuredTaskSupport structuredTaskSupport = new StructuredTaskSupport();

    /** The synthetic function the shim's thread bootstrap calls with the thread's {@code Runnable}. */
    public static final MethodRef ENTRY_METHOD =
            new MethodRef("java/lang/Thread", "$junoEntry", "(Ljava/lang/Runnable;)V");
    /** The dispatch site registered for {@link #ENTRY_METHOD}'s one {@code run()} call. */
    public static final InterfaceCallSite ENTRY_SITE = new InterfaceCallSite(ENTRY_METHOD, 0);
    public static final MethodRef RUNNABLE_RUN = new MethodRef("java/lang/Runnable", "run", "()V");

    private static final MethodRef START_VIRTUAL_THREAD =
            new MethodRef("java/lang/Thread", "startVirtualThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;");


    /** Records the synthetic {@code Runnable.run()} site when {@code called} starts a virtual thread. */
    public void registerEntry(MethodRef called, Map<InterfaceCallSite, MethodRef> interfaceCalls) {
        if (called.equals(START_VIRTUAL_THREAD)) {
            interfaceCalls.put(ENTRY_SITE, RUNNABLE_RUN);
        }
    }

    /**
     * Rejects a program that uses {@code java.lang.Thread} beyond the supported subset. Virtual threads are the only
     * thread a program creates ({@code Thread.startVirtualThread(Runnable)}), with {@code join()} and
     * {@code isAlive()} on the handle; platform threads, {@code Thread.ofVirtual()} builders and the other
     * {@code Thread} members are rejected. {@code Thread.sleep} and {@code Thread.yield} remain available.
     */
    public void validateCall(MethodRef called) {
        if (called.owner().equals("java/lang/Thread") && !intrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno does not support java.lang.Thread (" + called.displayName()
                    + "); create a virtual thread with Thread.startVirtualThread(Runnable) instead. Supported: "
                    + "startVirtualThread, join, isAlive, Thread.sleep and Thread.yield");
        }
    }

    /** The diagnostic for an interface call with no reachable implementation. */
    public String unresolvedMessage(InterfaceCallSite site, MethodRef interfaceMethod) {
        if (site.equals(ENTRY_SITE)) {
            return "Thread.startVirtualThread and StructuredTaskScope.fork need a Runnable: no reachable class or "
                    + "lambda implements java.lang.Runnable";
        }
        if (structuredTaskSupport.isEntrySite(site)) {
            return "StructuredTaskScope.fork needs a Callable: no reachable class or lambda implements "
                    + "java.util.concurrent.Callable";
        }
        if (scopedValueSupport.isEntrySite(site)) {
            return "ScopedValue.Carrier.call needs a CallableOp: no reachable lambda or class implements "
                    + "java.lang.ScopedValue.CallableOp";
        }
        return site.caller().displayName() + " at bytecode offset " + site.bytecodeOffset()
                + ": no reachable implementation of " + interfaceMethod.displayName();
    }

    /**
     * Whether {@code descriptor} is {@code Thread} (an arena-allocated handle) or {@code Runnable} (a pointer to
     * a closed-world implementation or lambda), the two JDK reference types a program may pass around and store.
     */
    public boolean isThreadType(String descriptor) {
        return descriptor.equals("Ljava/lang/Thread;") || descriptor.equals("Ljava/lang/Runnable;");
    }
}
