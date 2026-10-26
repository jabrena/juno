package io.github.jabrena.juno.hardware.net.http;

/**
 * A minimal, non-blocking HTTP/1.1 server recognized as compiler intrinsics by Juno, backed by
 * the Arduino {@code WiFiS3} library's {@code WiFiServer} on UNO R4 WiFi and by {@code
 * BridgeTCPServer} (a listener in {@code arduino-router} on Linux) on UNO Q. Requires an active
 * {@link io.github.jabrena.juno.hardware.net.Wifi#begin} or {@link io.github.jabrena.juno.hardware.net.Wifi#beginAP}
 * connection. {@link #begin(int)} serves plain HTTP on every board; {@link #beginTls} serves HTTPS on the ESP32
 * boards (Nano ESP32, M5Stack Cardputer) and on the UNO Q, whose MCU runs the TLS session over the bridge.
 *
 * <p>{@link #begin(int)} starts listening once. From then on, call {@link #accept} once per
 * {@code loop()} iteration: it returns immediately with {@code -1} when no client is currently
 * waiting, so the rest of {@code loop()} still runs every iteration, or reads a complete request
 * (method, path, and body) within a fixed 5-second deadline once a client has connected. After a
 * successful {@code accept()} (a return value of {@code 0} or more), {@link #method()} and
 * {@link #path()} report the request that was just read, and exactly one {@code respond} call
 * must follow to reply and close that connection before the next {@code accept()}.
 *
 * <p>{@code path()} is typically matched against known routes with {@link String#equals}, e.g.
 * {@code if (HttpServer.path().equals("/status")) ...}. {@link #respond(int, String, String)}'s
 * {@code body} may be a literal or any runtime {@code String} (e.g. a {@code StringBuilder}'s
 * {@code toString()}) — unlike every other intrinsic {@code String} parameter in Juno, it is not
 * restricted to a compile-time literal, since a literal's address and a runtime {@code String}'s
 * pooled address are both just a null-terminated {@code const char*} to this method's shim. That
 * runtime {@code String} is still bounded by Juno's small runtime-string pool, though; for a body
 * that might not fit, {@link #respond(int, String, StringBuilder)} instead streams a {@link
 * StringBuilder}'s current contents directly, with no such cap.
 */
public final class HttpServer {
    /** A reasonable default size for a caller's request-body buffer, in bytes. */
    public static final int DEFAULT_BODY_BUFFER_SIZE = 512;

    private HttpServer() {
    }

    /** Starts listening on {@code port}. Call once, typically right after {@link io.github.jabrena.juno.hardware.net.Wifi#begin} succeeds. */
    public static native void begin(int port);

    /**
     * Starts listening for HTTPS on {@code port} (usually 443), presenting {@code certificate} and proving it with
     * {@code privateKey}: both PEM text, typically a self-signed pair read from the SD card, so clients must trust the
     * certificate themselves (e.g. {@code curl --cacert server.crt}, or {@code -k}). The buffers are parsed here and
     * can be reused afterwards. On success the rest of the API is unchanged: {@link #accept} completes the TLS
     * handshake before it reads the request. Returns {@code false} when the certificate or key does not parse.
     * ESP32 boards (Nano ESP32, M5Stack Cardputer) and UNO Q only, one connection at a time; call it once, instead of
     * {@link #begin(int)}.
     */
    public static native boolean beginTls(int port, byte[] certificate, int certificateLength, byte[] privateKey,
            int privateKeyLength);

    /**
     * Returns immediately with {@code -1} when no client is currently waiting. Otherwise reads
     * the next request's body into {@code bodyBuffer} (truncated to its own length; {@code 0} for
     * a bodyless request such as a plain {@code GET}) within a fixed 5-second deadline, and
     * returns the number of body bytes written. Pass {@code bodyBuffer}'s own array length
     * explicitly, since Juno has no runtime {@code .length} for an array method parameter.
     */
    public static native int accept(byte[] bodyBuffer, int bodyBufferLength);

    /** The HTTP method ({@code "GET"}, {@code "POST"}, ...) of the request from the most recent {@link #accept}. */
    public static native String method();

    /**
     * The request path (e.g. {@code "/status"}) of the request from the most recent {@link #accept}, up to 95
     * characters; the next {@code accept} overwrites it.
     */
    public static native String path();

    /**
     * The {@code Content-Length} header of the request from the most recent {@link #accept}, or {@code -1} when it
     * had none. It is the size the client declared, so a server can reject a body larger than its buffer (which
     * {@code accept} silently truncates) before parsing it.
     */
    public static native int contentLength();

    /**
     * Copies the value of request header {@code name} from the most recent {@link #accept} into {@code buffer},
     * truncated to {@code bufferLength}, and returns the number of bytes copied, or {@code -1} when the request has no
     * such header. The name is matched case-insensitively and must be a string literal, such as
     * {@code "Mailbox-API-Key"}; spaces around the value are trimmed. Only the first 384 bytes of the request's
     * header lines are kept, so a header past that reads as absent.
     */
    public static native int header(String name, byte[] buffer, int bufferLength);

    /** Replies with {@code body} (a literal or a runtime {@code String}), then closes the connection. */
    public static native void respond(int status, String contentType, String body);

    /** Replies with {@code body}'s current contents (built at runtime), then closes the connection. */
    public static native void respond(int status, String contentType, StringBuilder body);
}
