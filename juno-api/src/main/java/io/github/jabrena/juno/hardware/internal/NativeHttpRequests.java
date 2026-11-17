package io.github.jabrena.juno.hardware.internal;

import org.jspecify.annotations.Nullable;

/**
 * The board's HTTP and HTTPS requests behind the board-side {@code java.net.http} client. The request target
 * ({@code path?query}) is a {@code String}, so its length is limited only by the arena. Each method is a compiler
 * intrinsic with no body on the JVM; a program sends requests with {@code java.net.http.HttpClient} instead.
 */
public final class NativeHttpRequests {

    private NativeHttpRequests() {
    }

    /**
     * Sets how long every later request waits, in milliseconds: on ESP32 boards to connect and then again for the
     * response, elsewhere for the response only. A value of {@code 0} or less is ignored; the default is 5000.
     */
    public static native void setTimeout(int millis);

    /**
     * Sends a plain-HTTP request for {@code target} to {@code host:port} and returns how many body bytes it wrote
     * into {@code bodyBuffer}, or {@code -1} when it could not be sent. The first {@code requestHeadersLength} bytes of
     * {@code requestHeadersBuffer} are complete header lines, each ending in {@code \r\n}; a negative
     * {@code requestBodyLength} sends no body. {@code statusAndHeadersLength} receives the status at index 0 and the
     * header bytes written into {@code headersBuffer} at index 1.
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

    /**
     * Makes later HTTPS requests trust only the certificates in {@code pem}, a PEM chain {@code length} characters
     * long, or, when it is {@code null}, the board's own trust again. The board keeps a pointer to {@code pem}, so it
     * must stay reachable while it is in use. Returns {@code false} when the board cannot take trust anchors at
     * runtime: only the ESP32 boards and the Raspberry Pi Pico 2 W can.
     */
    public static native boolean setTrustedCertificate(@Nullable String pem, int length);

    /** The HTTPS counterpart of {@link #requestHttp}. */
    public static native int requestHttps(String host, int port, String method, String target,
            byte[] requestHeadersBuffer, int requestHeadersLength,
            byte[] requestBodyBuffer, int requestBodyLength,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);
}
