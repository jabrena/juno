package io.github.jabrena.juno.hardware.internal;

/**
 * The board's HTTP and HTTPS request for the board-side {@code java.net.http} client: like
 * {@link io.github.jabrena.juno.hardware.net.http.HttpClient#requestHttp}, but the request target ({@code path?query})
 * is a {@code String}, so its length is limited only by the arena rather than by a fixed buffer. Both are intrinsics;
 * they have no body on the JVM.
 */
public final class NativeHttpRequests {

    private NativeHttpRequests() {
    }

    /**
     * Sends a plain-HTTP request for {@code target} to {@code host:port} and returns how many body bytes it wrote
     * into {@code bodyBuffer}, or {@code -1} when it could not be sent. The other arguments are those of
     * {@link io.github.jabrena.juno.hardware.net.http.HttpClient#requestHttp}.
     */
    public static native int requestHttp(String host, int port, String method, String target,
            byte[] requestHeadersBuffer, int requestHeadersLength,
            byte[] requestBodyBuffer, int requestBodyLength,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** The HTTPS counterpart of {@link #requestHttp}. */
    public static native int requestHttps(String host, int port, String method, String target,
            byte[] requestHeadersBuffer, int requestHeadersLength,
            byte[] requestBodyBuffer, int requestBodyLength,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);
}
