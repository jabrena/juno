package io.github.jabrena.juno.hardware.internal;

/**
 * Which URI schemes a program sends HTTP requests to, answered when the program is linked. The compiler reads every
 * reachable {@code URI.create(...)} call; when each one is given a constant string, a branch on these queries is
 * decided for that program, so the request code of a scheme it never uses is not linked — nor the shim behind it, such
 * as the TLS client of {@code https}. A URI built from runtime data keeps both. On the JVM both answer {@code true}.
 */
public final class NativeHttpSchemes {
    private NativeHttpSchemes() {
    }

    /** Whether the program may send a request to an {@code http} URI. */
    public static boolean http() {
        return true;
    }

    /** Whether the program may send a request to an {@code https} URI. */
    public static boolean https() {
        return true;
    }
}
