package io.github.jabrena.juno.hardware.internal;

/**
 * What a program does with {@code java.net.http} responses, answered when the program is linked. The compiler reads
 * the program's own calls: a branch on these queries is decided for that program, so a program that never reads a
 * response's headers keeps a small header buffer, and one that never follows redirects, sets its own method or a
 * timeout links no code for them. On the JVM every query answers {@code true}.
 */
public final class NativeHttpFeatures {
    private NativeHttpFeatures() {
    }

    /** Whether the program reads {@code HttpResponse.headers()} or follows redirects, which read {@code Location}. */
    public static boolean responseHeaders() {
        return true;
    }

    /** Whether the program asks a client to follow redirects, with {@code HttpClient.Builder.followRedirects}. */
    public static boolean redirects() {
        return true;
    }

    /**
     * Whether the program sets a method of its own, with {@code HttpRequest.Builder.method}: the one way to send a
     * {@code CONNECT}, whose request target needs code of its own.
     */
    public static boolean customMethods() {
        return true;
    }

    /**
     * Whether the program sets a timeout, with {@code HttpRequest.Builder.timeout} or
     * {@code HttpClient.Builder.connectTimeout}; any other program keeps the board's own timeout.
     */
    public static boolean timeouts() {
        return true;
    }
}
