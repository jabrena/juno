package io.github.jabrena.juno.hardware.internal;

/**
 * The board's single-connection HTTP and HTTPS listener behind the board-side {@code com.sun.net.httpserver} API. A
 * program serves requests with {@code com.sun.net.httpserver.HttpServer} or {@code HttpsServer} instead; Juno's own
 * {@code WifiProvisioner} answers with {@link #respond} directly. Each method is a compiler intrinsic with no body on
 * the JVM.
 *
 * <p>{@link #begin(int)} or {@link #beginTls} starts listening once. {@link #accept} returns {@code -1} at once when no
 * client is waiting, or reads one complete request (method, path, headers and body) within a fixed five-second
 * deadline. After a successful {@code accept()}, {@link #method()}, {@link #path()}, {@link #contentLength()} and
 * {@link #header} describe that request, and one response must close the connection before the next {@code accept()}.
 */
public final class NativeHttpServer {
    private NativeHttpServer() {
    }

    /** Starts listening for plain HTTP on {@code port}, once Wi-Fi is connected or an access point is up. */
    public static native void begin(int port);

    /**
     * Starts listening for HTTPS on {@code port}, presenting {@code certificate} (PEM) and proving it with
     * {@code privateKey} (PEM, or PKCS#8 or SEC1 DER). The buffers are parsed here and can be wiped afterwards.
     * Returns {@code false} when the certificate or key does not parse. ESP32 boards, the Raspberry Pi Pico 2 W and
     * the Portenta H7 only.
     */
    public static native boolean beginTls(int port, byte[] certificate, int certificateLength, byte[] privateKey,
            int privateKeyLength);

    /**
     * Returns {@code -1} when no client is waiting; otherwise reads the next request's body into {@code bodyBuffer},
     * truncated to {@code bodyBufferLength}, and returns how many body bytes it wrote.
     */
    public static native int accept(byte[] bodyBuffer, int bodyBufferLength);

    /** The method ({@code "GET"}, {@code "POST"}, ...) of the request from the last {@link #accept}. */
    public static native String method();

    /** The request target (e.g. {@code "/status"}) from the last {@link #accept}, up to 95 characters. */
    public static native String path();

    /** The request's declared {@code Content-Length}, or {@code -1} when it had none. */
    public static native int contentLength();

    /**
     * Copies the value of request header {@code name}, matched case-insensitively with spaces around the value
     * trimmed, into {@code buffer} and returns its length, or {@code -1} when the request has no such header. Only the
     * first 384 bytes of the request's header lines are kept.
     */
    public static native int header(String name, byte[] buffer, int bufferLength);

    /** Replies with {@code body}, then closes the connection; {@code contentType} must be a string literal. */
    public static native void respond(int status, String contentType, String body);
}
