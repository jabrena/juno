package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.MethodRef;

import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;


/**
 * The closed-world plumbing behind {@code java.lang.Thread}: a started thread runs its {@code Runnable} on a
 * stack of its own, entered from the runtime shim, so the linker registers one synthetic
 * {@code Runnable.run()} call site that resolves against every reachable {@code Runnable} (classes and lambdas)
 * and the lowering turns it into the generated {@link #ENTRY_METHOD thread entry} function.
 */
public final class ThreadSupport {
    /** The synthetic function the shim's thread bootstrap calls with the thread's {@code Runnable}. */
    public static final MethodRef ENTRY_METHOD =
            new MethodRef("java/lang/Thread", "$junoEntry", "(Ljava/lang/Runnable;)V");
    /** The dispatch site registered for {@link #ENTRY_METHOD}'s one {@code run()} call. */
    public static final InterfaceCallSite ENTRY_SITE = new InterfaceCallSite(ENTRY_METHOD, 0);
    public static final MethodRef RUNNABLE_RUN = new MethodRef("java/lang/Runnable", "run", "()V");

    private ThreadSupport() {
    }

    /**
     * Rejects a program that uses {@code java.lang.Thread} directly. The cooperative thread runtime stays, as
     * {@code StructuredTaskScope} forks its subtasks onto it, but only {@code Thread.sleep} and
     * {@code Thread.yield} (which a subtask calls) are part of the language subset.
     */
    public static void validateCall(MethodRef called) {
        if (called.owner().equals("java/lang/Thread") && !IntrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno does not support java.lang.Thread (" + called.displayName()
                    + "); fork the work with StructuredTaskScope instead. Only Thread.sleep and Thread.yield "
                    + "are available, inside a forked subtask");
        }
    }

    /** The diagnostic for an interface call with no reachable implementation. */
    public static String unresolvedMessage(InterfaceCallSite site, MethodRef interfaceMethod) {
        if (site.equals(ENTRY_SITE)) {
            return "StructuredTaskScope.fork needs a Runnable: no reachable class or lambda implements java.lang.Runnable";
        }
        if (StructuredTaskSupport.isEntrySite(site)) {
            return "StructuredTaskScope.fork needs a Callable: no reachable class or lambda implements "
                    + "java.util.concurrent.Callable";
        }
        if (ScopedValueSupport.isEntrySite(site)) {
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
    public static boolean isThreadType(String descriptor) {
        return descriptor.equals("Ljava/lang/Thread;") || descriptor.equals("Ljava/lang/Runnable;");
    }
}
