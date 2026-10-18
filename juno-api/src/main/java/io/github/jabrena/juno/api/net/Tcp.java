package io.github.jabrena.juno.api.net;

/**
 * Allocation-free TCP transport recognized as compiler intrinsics by Juno.
 *
 * <p>One TCP connection is available to a Juno program. Open it with an IPv4 endpoint, write a
 * caller-owned request, then read an exact number of response bytes into a caller-owned buffer.
 * Call {@link #close()} after every exchange. The transport has no protocol knowledge: framing,
 * encoding, and response parsing belong to the Java API layered on top of it.
 */
public final class Tcp {
    private Tcp() {
    }

    /** Opens a TCP connection to the first four IPv4 octets in {@code endpoint}. */
    public static native boolean open(int[] endpoint, int port);

    /** Writes {@code length} bytes and returns the number written, or {@code -1} on failure. */
    public static native int write(byte[] payload, int length);

    /**
     * Reads exactly {@code length} bytes into {@code payload} beginning at {@code offset} before
     * {@code timeoutMillis} expires. Returns {@code length}, or {@code -1} on timeout, disconnect,
     * or invalid input.
     */
    public static native int read(byte[] payload, int offset, int length, int timeoutMillis);

    /** Closes the current TCP connection, if any. */
    public static native void close();
}
