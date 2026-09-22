package io.github.jabrena.juno.api.io.net.http;

/**
 * A minimal HTTPS client recognized as compiler intrinsics by Juno, backed by Arduino's
 * {@code WiFiSSLClient} and the CA certificate bundle installed in the UNO R4 WiFi firmware.
 * Requires {@code @Board(ArduinoUnoR4WiFi.class)} and an active {@link io.github.jabrena.juno.api.io.net.Wifi#begin} connection.
 *
 * <p>{@code host}, {@code path}, and request bodies must be compile-time strings. Responses use
 * caller-owned buffers and have the same shape, five-second timeout, and
 * {@code statusAndHeadersLength} out-param as {@link HttpClient}. TLS setup or
 * certificate-validation failures are reported as {@code -1} with {@code statusAndHeadersLength[0]}
 * left at {@code 0}.
 */
public final class HttpsClient {
    /** A reasonable default size for a caller's body/headers buffer, in bytes. */
    public static final int DEFAULT_RESPONSE_BUFFER_SIZE = 1024;

    private HttpsClient() {
    }

    public static native int get(String host, int port, String path,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    public static native int post(String host, int port, String path, String body,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** Sends a bodyless HTTPS DELETE request. */
    public static native int delete(String host, int port, String path,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** Sends an HTTPS PATCH request with a compile-time JSON body. */
    public static native int patch(String host, int port, String path, String body,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** Sends an HTTPS QUERY request with a compile-time JSON body. */
    public static native int query(String host, int port, String path, String body,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);
}
