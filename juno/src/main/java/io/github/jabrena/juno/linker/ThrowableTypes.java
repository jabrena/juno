package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Map;

/**
 * The closed-world exception hierarchy: a fixed set of built-in JDK throwables (which Juno models
 * itself, since their class files are never on the classpath) plus any program class whose superclass
 * chain reaches one of them.
 *
 * <p>Every throwable object starts with a two-word header — its program-wide class id and its message
 * ({@code String} address or {@code 0}) — so {@code athrow} can match {@code catch} clauses at runtime
 * and {@code getMessage()} is a single load. Only the {@code ()} and {@code (String)} constructors are
 * modeled; exception causes, stack traces and suppression are not.
 */
public final class ThrowableTypes {
    /** Header bytes before a throwable's own fields: class id, then message. */
    public static final int HEADER_BYTES = 8;
    public static final int CLASS_ID_OFFSET = 0;
    public static final int MESSAGE_OFFSET = 4;

    private static final String NO_ARGUMENTS = "()V";
    private static final String MESSAGE_ONLY = "(Ljava/lang/String;)V";
    private static final String GET_MESSAGE = "()Ljava/lang/String;";

    /** Built-in throwable to its superclass ({@code ""} for {@code Throwable}). */
    private static final Map<String, String> BUILT_IN = Map.ofEntries(
            Map.entry("java/lang/Throwable", ""),
            Map.entry("java/lang/Exception", "java/lang/Throwable"),
            Map.entry("java/lang/Error", "java/lang/Throwable"),
            Map.entry("java/lang/RuntimeException", "java/lang/Exception"),
            Map.entry("java/lang/IllegalArgumentException", "java/lang/RuntimeException"),
            Map.entry("java/lang/NumberFormatException", "java/lang/IllegalArgumentException"),
            Map.entry("java/lang/IllegalStateException", "java/lang/RuntimeException"),
            Map.entry("java/lang/ArithmeticException", "java/lang/RuntimeException"),
            Map.entry("java/lang/IndexOutOfBoundsException", "java/lang/RuntimeException"),
            Map.entry("java/lang/ArrayIndexOutOfBoundsException", "java/lang/IndexOutOfBoundsException"),
            Map.entry("java/lang/StringIndexOutOfBoundsException", "java/lang/IndexOutOfBoundsException"),
            Map.entry("java/lang/NullPointerException", "java/lang/RuntimeException"),
            Map.entry("java/lang/UnsupportedOperationException", "java/lang/RuntimeException"),
            Map.entry("java/lang/ClassCastException", "java/lang/RuntimeException"),
            Map.entry("java/lang/NegativeArraySizeException", "java/lang/RuntimeException"),
            Map.entry("java/lang/InterruptedException", "java/lang/Exception"),
            Map.entry("java/util/NoSuchElementException", "java/lang/RuntimeException"),
            Map.entry("java/util/concurrent/ExecutionException", "java/lang/Exception"),
            Map.entry("java/util/concurrent/TimeoutException", "java/lang/Exception"),
            Map.entry("java/io/IOException", "java/lang/Exception"));

    private ThrowableTypes() {
    }

    public static boolean isBuiltIn(String className) {
        return BUILT_IN.containsKey(className);
    }

    public static boolean isThrowable(String className, Map<String, JavaClass> classes) {
        return isSubtype(className, "java/lang/Throwable", classes);
    }

    /** Whether {@code className} is {@code ancestor} or inherits from it. */
    public static boolean isSubtype(String className, String ancestor, Map<String, JavaClass> classes) {
        String current = className;
        while (current != null && !current.isEmpty()) {
            if (current.equals(ancestor)) {
                return true;
            }
            JavaClass programClass = classes.get(current);
            current = programClass != null ? programClass.superClassName() : BUILT_IN.get(current);
        }
        return false;
    }

    /** A built-in throwable's constructor, called directly or as a program exception's {@code super(...)}. */
    public static boolean isBuiltInConstructor(MethodRef called) {
        return called.name().equals("<init>") && isBuiltIn(called.owner());
    }

    /** Whether {@link #isBuiltInConstructor} {@code called} is one Juno models (no cause). */
    public static boolean isSupportedConstructor(MethodRef called) {
        return called.descriptor().equals(NO_ARGUMENTS) || called.descriptor().equals(MESSAGE_ONLY);
    }

    public static boolean takesMessage(MethodRef called) {
        return called.descriptor().equals(MESSAGE_ONLY);
    }

    /** {@code getMessage()} on any throwable static type, unless a program class overrides it. */
    public static boolean isGetMessage(MethodRef called, Map<String, JavaClass> classes) {
        if (!called.name().equals("getMessage") || !called.descriptor().equals(GET_MESSAGE)
                || !isThrowable(called.owner(), classes)) {
            return false;
        }
        for (JavaClass current = classes.get(called.owner()); current != null && current.superClassName() != null;
             current = classes.get(current.superClassName())) {
            if (current.findMethod("getMessage", GET_MESSAGE) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code addSuppressed(Throwable)}, which javac emits in try-with-resources when {@code close()} also throws.
     * Juno keeps no suppressed list, so the call is dropped and the primary exception propagates unchanged.
     */
    public static boolean isAddSuppressed(MethodRef called, Map<String, JavaClass> classes) {
        return called.name().equals("addSuppressed") && called.descriptor().equals("(Ljava/lang/Throwable;)V")
                && isThrowable(called.owner(), classes);
    }
}
