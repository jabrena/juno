package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Map;

/** Closed-world plumbing for Juno's Java 21 {@code StructuredTaskScope} policy subset. */
public final class StructuredTaskSupport {
    public static final String SCOPE = "java/util/concurrent/StructuredTaskScope";
    public static final String SHUTDOWN_ON_FAILURE = SCOPE + "$ShutdownOnFailure";
    public static final String SHUTDOWN_ON_SUCCESS = SCOPE + "$ShutdownOnSuccess";
    public static final String SUBTASK = SCOPE + "$Subtask";
    public static final String CALLABLE = "java/util/concurrent/Callable";

    public static final MethodRef ENTRY_METHOD =
            new MethodRef(SCOPE, "$junoTaskEntry", "(Ljava/util/concurrent/Callable;)Ljava/lang/Object;");
    public static final InterfaceCallSite ENTRY_SITE = new InterfaceCallSite(ENTRY_METHOD, 0);
    public static final MethodRef CALLABLE_CALL = new MethodRef(CALLABLE, "call", "()Ljava/lang/Object;");

    private StructuredTaskSupport() {
    }

    public static boolean isScopeConstructor(MethodRef called) {
        return called.name().equals("<init>") && called.descriptor().equals("()V")
                && (called.owner().equals(SHUTDOWN_ON_FAILURE) || called.owner().equals(SHUTDOWN_ON_SUCCESS));
    }

    public static boolean needsEntry(MethodRef called) {
        return isScopeOwner(called.owner()) && called.name().equals("fork")
                && called.descriptor().equals("(Ljava/util/concurrent/Callable;)L" + SUBTASK + ";");
    }

    public static void registerEntry(MethodRef called, Map<InterfaceCallSite, MethodRef> interfaceCalls) {
        if (needsEntry(called)) {
            interfaceCalls.put(ENTRY_SITE, CALLABLE_CALL);
        }
    }

    public static boolean isCallableCall(MethodRef called) {
        return called.equals(CALLABLE_CALL);
    }

    public static boolean isEntrySite(InterfaceCallSite site) {
        return site.equals(ENTRY_SITE);
    }

    public static boolean isScopeOwner(String owner) {
        return owner.equals(SCOPE) || owner.equals(SHUTDOWN_ON_FAILURE) || owner.equals(SHUTDOWN_ON_SUCCESS);
    }

    public static boolean isStructuredTaskOwner(String owner) {
        return isScopeOwner(owner) || owner.equals(SUBTASK);
    }

    /** Rejects API calls outside the deliberately small policy subset with a useful diagnostic. */
    public static void validateCall(MethodRef called) {
        if (isStructuredTaskOwner(called.owner()) && !IntrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno's restricted StructuredTaskScope subset supports only "
                    + "ShutdownOnFailure/ShutdownOnSuccess construction, fork(Callable), join(), "
                    + "throwIfFailed(), result(), Subtask.get(), and close(): " + called.displayName());
        }
    }
}
