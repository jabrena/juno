package io.github.jabrena.juno.api.io.net.http;

/**
 * A minimal HTTP/1.1 client recognized as compiler intrinsics by Juno, backed by the Arduino
 * {@code WiFiS3} library's {@code WiFiClient}. Requires {@code @Board(ArduinoUnoR4WiFi.class)}
 * (the default board) and an active {@link io.github.jabrena.juno.api.io.net.Wifi#begin} connection.
 *
 * <p>{@code host}/{@code path} (and {@code body} for {@link #post}, {@link #patch}, and
 * {@link #query}) must each be a compile-time
 * constant: either a string literal, or {@code System.getenv("NAME")} of a literal
 * environment-variable name, exactly like {@link io.github.jabrena.juno.api.io.net.Wifi#begin}'s credentials. Juno has no heap, so
 * none of these can be a runtime-computed {@code String} — this also means these methods must be
 * called directly with the literal in hand, never forwarded through another method's own
 * {@code String} parameter.
 *
 * <p>The response body is written into {@code bodyBuffer} and the raw response headers into
 * {@code headersBuffer} (both caller-owned, fixed-size {@code byte[]}), each starting at index 0
 * and truncated to its own length if longer — pass each buffer's own array length explicitly,
 * since Juno has no runtime {@code .length} for an array method parameter. Each method returns the
 * number of body bytes written, or {@code -1} if the connection failed. {@code statusAndHeadersLength}
 * (a caller-owned {@code int[2]}, allocated once outside any loop like the buffers themselves) is
 * filled with the HTTP status code at index 0 (or {@code 0} if the connection failed before a
 * status line was received) and the number of bytes written into {@code headersBuffer} at index 1.
 * A request that doesn't complete within a fixed 5-second timeout is abandoned and returns
 * whatever was captured so far.
 */
public final class HttpClient {
    /** A reasonable default size for a caller's body/headers buffer, in bytes. */
    public static final int DEFAULT_RESPONSE_BUFFER_SIZE = 1024;

    private HttpClient() {
    }

    public static native int get(String host, int port, String path,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    public static native int post(String host, int port, String path, String body,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** Sends a bodyless HTTP DELETE request. */
    public static native int delete(String host, int port, String path,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** Sends an HTTP PATCH request with a compile-time JSON body. */
    public static native int patch(String host, int port, String path, String body,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /**
     * Sends a safe, idempotent HTTP QUERY request with a compile-time JSON body, as defined by
     * RFC 10008.
     */
    public static native int query(String host, int port, String path, String body,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);
}
