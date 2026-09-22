package io.github.jabrena.juno.api.io.net.http;

/**
 * A minimal, non-blocking HTTP/1.1 server recognized as compiler intrinsics by Juno, backed by
 * the Arduino {@code WiFiS3} library's {@code WiFiServer}. Requires
 * {@code @Board(ArduinoUnoR4WiFi.class)} (the default board) and an active {@link io.github.jabrena.juno.api.io.net.Wifi#begin}
 * connection. Plain HTTP only — there is no TLS-server equivalent of {@link HttpsClient}.
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

    /** Starts listening on {@code port}. Call once, typically right after {@link io.github.jabrena.juno.api.io.net.Wifi#begin} succeeds. */
    public static native void begin(int port);

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

    /** The request path (e.g. {@code "/status"}) of the request from the most recent {@link #accept}. */
    public static native String path();

    /** Replies with {@code body} (a literal or a runtime {@code String}), then closes the connection. */
    public static native void respond(int status, String contentType, String body);

    /** Replies with {@code body}'s current contents (built at runtime), then closes the connection. */
    public static native void respond(int status, String contentType, StringBuilder body);
}
