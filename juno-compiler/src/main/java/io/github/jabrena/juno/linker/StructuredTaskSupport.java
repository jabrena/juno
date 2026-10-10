package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Closed-world plumbing for Juno's JDK 27 preview {@code StructuredTaskScope} subset. */
public final class StructuredTaskSupport {
    public static final String SCOPE = "java/util/concurrent/StructuredTaskScope";
    public static final String JOINER = SCOPE + "$Joiner";
    public static final String CONFIGURATION = SCOPE + "$Configuration";
    public static final String SUBTASK = SCOPE + "$Subtask";
    public static final String STATE = SUBTASK + "$State";
    public static final String LEGACY_SHUTDOWN_ON_FAILURE = SCOPE + "$ShutdownOnFailure";
    public static final String LEGACY_SHUTDOWN_ON_SUCCESS = SCOPE + "$ShutdownOnSuccess";
    public static final String CALLABLE = "java/util/concurrent/Callable";

    public static final int JOINER_ALL_SUCCESSFUL = 1;
    public static final int JOINER_ANY_SUCCESSFUL = 2;
    public static final int JOINER_AWAIT_ALL_SUCCESSFUL = 3;
    public static final int JOINER_AWAIT_ALL = 4;

    public static final MethodRef ENTRY_METHOD =
            new MethodRef(SCOPE, "$junoTaskEntry", "(Ljava/util/concurrent/Callable;)Ljava/lang/Object;");
    public static final InterfaceCallSite ENTRY_SITE = new InterfaceCallSite(ENTRY_METHOD, 0);
    public static final MethodRef CALLABLE_CALL = new MethodRef(CALLABLE, "call", "()Ljava/lang/Object;");

    private StructuredTaskSupport() {
    }

    public static boolean needsEntry(MethodRef called) {
        return called.owner().equals(SCOPE) && called.name().equals("fork")
                && (called.descriptor().equals("(Ljava/util/concurrent/Callable;)L" + SUBTASK + ";")
                || called.descriptor().equals("(Ljava/lang/Runnable;)L" + SUBTASK + ";"));
    }

    public static void registerEntry(MethodRef called, Map<InterfaceCallSite, MethodRef> interfaceCalls) {
        if (needsEntry(called) && called.descriptor().startsWith("(Ljava/util/concurrent/Callable;")) {
            interfaceCalls.put(ENTRY_SITE, CALLABLE_CALL);
        } else if (needsEntry(called)) {
            interfaceCalls.put(ThreadSupport.ENTRY_SITE, ThreadSupport.RUNNABLE_RUN);
        }
    }

    public static boolean isCallableCall(MethodRef called) {
        return called.equals(CALLABLE_CALL);
    }

    public static boolean isEntrySite(InterfaceCallSite site) {
        return site.equals(ENTRY_SITE);
    }

    public static boolean isScopeOwner(String owner) {
        return owner.equals(SCOPE);
    }

    public static boolean isStructuredTaskOwner(String owner) {
        return isScopeOwner(owner) || owner.equals(JOINER) || owner.equals(CONFIGURATION)
                || owner.equals(SUBTASK) || owner.equals(STATE)
                || owner.equals(LEGACY_SHUTDOWN_ON_FAILURE) || owner.equals(LEGACY_SHUTDOWN_ON_SUCCESS);
    }

    /** Runtime policy token represented by a supported built-in {@code Joiner} factory, or {@code null}. */
    public static @Nullable Integer joinerPolicy(MethodRef called) {
        if (!called.owner().equals(JOINER) || !called.descriptor().equals("()L" + JOINER + ";")) {
            return null;
        }
        return switch (called.name()) {
            case "allSuccessfulOrThrow" -> JOINER_ALL_SUCCESSFUL;
            case "anySuccessfulOrThrow" -> JOINER_ANY_SUCCESSFUL;
            case "awaitAllSuccessfulOrThrow" -> JOINER_AWAIT_ALL_SUCCESSFUL;
            default -> null;
        };
    }

    /** Rejects user implementations because Juno lowers only the four built-in policy tokens. */
    public static void validateNoCustomJoiners(Set<String> instantiatedClasses, Collection<LambdaSite> lambdaSites,
                                               Map<String, JavaClass> classes) {
        for (String className : instantiatedClasses) {
            if (implementsInterface(className, JOINER, classes, new TreeSet<>())) {
                throw new CompileException("Juno's JDK 27 StructuredTaskScope subset does not support custom "
                        + "Joiner implementations: " + className.replace('/', '.'));
            }
        }
        lambdaSites.stream().filter(site -> site.interfaceMethod().owner().equals(JOINER)).findFirst()
                .ifPresent(site -> {
                    throw new CompileException("Juno's JDK 27 StructuredTaskScope subset does not support custom "
                            + "Joiner implementations: " + site.syntheticClassName().replace('/', '.'));
                });
    }

    private static boolean implementsInterface(String typeName, String interfaceName,
                                                Map<String, JavaClass> classes,
                                                Set<String> visited) {
        if (!visited.add(typeName)) return false;
        JavaClass type = classes.get(typeName);
        if (type == null) return false;
        for (String direct : type.interfaces()) {
            if (direct.equals(interfaceName) || implementsInterface(direct, interfaceName, classes, visited)) {
                return true;
            }
        }
        return type.superClassName() != null
                && implementsInterface(type.superClassName(), interfaceName, classes, visited);
    }

    /** Rejects API calls outside the deliberately small JDK 25 subset with a useful diagnostic. */
    public static void validateCall(MethodRef called) {
        if (isStructuredTaskOwner(called.owner()) && !IntrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno's JDK 27 StructuredTaskScope subset supports only open(), "
                    + "open(Joiner), the four built-in non-predicate Joiner factories, fork(Callable), "
                    + "fork(Runnable), join(), isCancelled(), close(), and Subtask.state()/get()/exception(); "
                    + "custom Joiner implementations, allUntil, Configuration, and the Java 21 policy classes, the Joiner factory overloads that take an exception-mapping Function, "
                    + "are unsupported: " + called.displayName());
        }
    }
}
