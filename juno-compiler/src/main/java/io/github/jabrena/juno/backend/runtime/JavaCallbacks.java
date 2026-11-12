package io.github.jabrena.juno.backend.runtime;

/**
 * Runtime code that calls back into Java ({@code ScopedValue.run}, a barrier action, a task configuration). With
 * several carriers the runtime call holds the runtime lock and marks its carrier out of Java; the Java it calls back
 * must run like any other Java, so the callback gives up the lock entirely and marks the carrier in Java until it
 * returns ({@code juno_java_callback_begin}/{@code juno_java_callback_end} in {@code common/carrier-gc.cpp}). One
 * carrier needs none of it, so its output is unchanged.
 */
final class JavaCallbacks {
    private static final String DECLARATIONS = """
            extern "C" uint32_t juno_java_callback_begin();
            extern "C" void juno_java_callback_end(uint32_t depth);
            """;

    private final int carrierCount;

    JavaCallbacks(int carrierCount) {
        this.carrierCount = carrierCount;
    }

    /** Brackets the statement {@code call} (found exactly as written in {@code source}) as a callback into Java. */
    String around(String source, String call) {
        if (carrierCount == 1 || !source.contains(call)) return source;
        String indent = indentOf(source, call);
        return source.replace(call, "uint32_t juno_callback_depth = juno_java_callback_begin();\n" + indent + call
                + "\n" + indent + "juno_java_callback_end(juno_callback_depth);");
    }

    /** The declarations a source using {@link #around} needs ahead of it, when it needs any. */
    String declarations() {
        return carrierCount == 1 ? "" : DECLARATIONS;
    }

    private static String indentOf(String source, String call) {
        int start = source.lastIndexOf('\n', source.indexOf(call)) + 1;
        int end = start;
        while (end < source.length() && source.charAt(end) == ' ') end++;
        return source.substring(start, end);
    }
}
