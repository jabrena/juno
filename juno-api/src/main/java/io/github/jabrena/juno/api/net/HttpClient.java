package io.github.jabrena.juno.api.net;

/**
 * A minimal HTTP/1.1 client recognized as compiler intrinsics by Juno, backed by the Arduino
 * {@code WiFiS3} library's {@code WiFiClient}. Requires {@code @Board(ArduinoUnoR4WiFi.class)}
 * (the default board) and an active {@link Wifi#begin} connection.
 *
 * <p>{@code host}/{@code path} (and {@code body} for {@link #post}) must each be a compile-time
 * constant: either a string literal, or {@code System.getenv("NAME")} of a literal
 * environment-variable name, exactly like {@link Wifi#begin}'s credentials. Juno has no heap, so
 * none of these can be a runtime-computed {@code String}.
 *
 * <p>The response body is written into {@code responseBuffer} (a caller-owned, fixed-size
 * {@code byte[]}), starting at index 0 and truncated to {@code responseBufferLength} bytes if the
 * body is longer — pass the buffer's own array length explicitly, since Juno has no runtime
 * {@code .length} for an array method parameter. Both methods return the number of body bytes
 * written, or {@code -1} if the connection failed. Only the response body is captured; the HTTP
 * status line and headers are consumed and discarded. A request that doesn't complete within a
 * fixed 5-second timeout is abandoned and returns whatever was captured so far.
 */
public final class HttpClient {
    private HttpClient() {
    }

    public static native int get(String host, int port, String path,
            byte[] responseBuffer, int responseBufferLength);

    public static native int post(String host, int port, String path, String body,
            byte[] responseBuffer, int responseBufferLength);
}
