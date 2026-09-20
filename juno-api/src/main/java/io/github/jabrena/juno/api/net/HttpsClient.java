package io.github.jabrena.juno.api.net;

/**
 * A minimal HTTPS client recognized as compiler intrinsics by Juno, backed by Arduino's
 * {@code WiFiSSLClient} and the CA certificate bundle installed in the UNO R4 WiFi firmware.
 * Requires {@code @Board(ArduinoUnoR4WiFi.class)} and an active {@link Wifi#begin} connection.
 *
 * <p>{@code host}, {@code path}, and request bodies must be compile-time strings. Responses use a
 * caller-owned buffer and have the same return values and five-second timeout as {@link HttpClient}.
 * TLS setup or certificate-validation failures are reported as {@code -1}.
 */
public final class HttpsClient {
    private HttpsClient() {
    }

    public static native int get(String host, int port, String path,
            byte[] responseBuffer, int responseBufferLength);

    public static native int post(String host, int port, String path, String body,
            byte[] responseBuffer, int responseBufferLength);

    /** Sends a bodyless HTTPS DELETE request. */
    public static native int delete(String host, int port, String path,
            byte[] responseBuffer, int responseBufferLength);

    /** Sends an HTTPS PATCH request with a compile-time JSON body. */
    public static native int patch(String host, int port, String path, String body,
            byte[] responseBuffer, int responseBufferLength);

    /** Sends an HTTPS QUERY request with a compile-time JSON body. */
    public static native int query(String host, int port, String path, String body,
            byte[] responseBuffer, int responseBufferLength);
}
