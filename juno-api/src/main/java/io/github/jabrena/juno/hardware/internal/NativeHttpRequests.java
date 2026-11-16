package io.github.jabrena.juno.hardware.internal;

import org.jspecify.annotations.Nullable;

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

    /**
     * Sends a plain-HTTP request as {@link #requestHttp} does, but returns the body as a {@code String} of exactly
     * the bytes received, with no size limit but the arena's: a body with a {@code Content-Length} is allocated once,
     * at that size, one without it grows as it arrives. Returns {@code null} when the request could not be sent, or
     * when the body did not fit in the arena, in which case {@code statusAndHeadersLength[2]} is {@code 1}; so that
     * array has three elements. The body is not decoded: a {@code NUL} byte in it ends the {@code String}.
     */
    public static native @Nullable String requestHttpForText(String host, int port, String method, String target,
            byte[] requestHeadersBuffer, int requestHeadersLength,
            byte[] requestBodyBuffer, int requestBodyLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** The HTTPS counterpart of {@link #requestHttpForText}. */
    public static native @Nullable String requestHttpsForText(String host, int port, String method, String target,
            byte[] requestHeadersBuffer, int requestHeadersLength,
            byte[] requestBodyBuffer, int requestBodyLength,
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
