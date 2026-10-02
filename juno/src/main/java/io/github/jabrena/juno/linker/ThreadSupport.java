package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Map;

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

    private static final MethodRef THREAD_CONSTRUCTOR =
            new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V");
    private static final MethodRef THREAD_START = new MethodRef("java/lang/Thread", "start", "()V");

    private ThreadSupport() {
    }

    /** Whether calling {@code called} means the program creates or runs a thread, so it needs the entry function. */
    public static boolean needsEntry(MethodRef called) {
        return called.equals(THREAD_CONSTRUCTOR) || called.equals(THREAD_START);
    }

    /** Records the synthetic {@code Runnable.run()} site when {@code called} creates or starts a thread. */
    public static void registerEntry(MethodRef called, Map<InterfaceCallSite, MethodRef> interfaceCalls) {
        if (needsEntry(called)) {
            interfaceCalls.put(ENTRY_SITE, RUNNABLE_RUN);
        }
    }

    /** The diagnostic for an interface call with no reachable implementation. */
    public static String unresolvedMessage(InterfaceCallSite site, MethodRef interfaceMethod) {
        if (site.equals(ENTRY_SITE)) {
            return "Thread needs a Runnable: no reachable class or lambda implements java.lang.Runnable";
        }
        if (StructuredTaskSupport.isEntrySite(site)) {
            return "StructuredTaskScope.fork needs a Callable: no reachable class or lambda implements "
                    + "java.util.concurrent.Callable";
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
