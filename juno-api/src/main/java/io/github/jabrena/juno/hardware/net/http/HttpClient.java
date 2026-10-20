package io.github.jabrena.juno.hardware.net.http;

/**
 * A minimal HTTP/1.1 client recognized as compiler intrinsics by Juno. As with {@code java.net.http.HttpClient}, the
 * URL decides how a request travels: an {@code https://} URL goes over TLS (Arduino's {@code WiFiSSLClient} and its
 * firmware CA bundle on UNO R4 WiFi, or Linux TLS through {@code Arduino_RouterBridge} on UNO Q), an {@code http://}
 * URL over a plain {@code WiFiClient}. The port defaults to 443 or 80 and can be given explicitly, as in
 * {@code http://192.168.1.10:8080/led}. Requires an active {@link io.github.jabrena.juno.hardware.net.Wifi#begin}
 * connection or bridge, and a board that supports the scheme: the UNO Q has no plain-HTTP client, for example.
 *
 * <p>The {@code url} (and {@code body} for {@link #post}, {@link #patch} and {@link #query}) must each be a
 * compile-time constant: a string literal, or {@code System.getenv("NAME")} of a literal environment-variable name,
 * like {@link io.github.jabrena.juno.hardware.net.Wifi#begin}'s credentials. The compiler splits the URL into scheme,
 * host, port and path, so nothing is parsed on the board. Call these methods with the literal in hand, never through
 * another method's own {@code String} parameter. For request headers or a body that depend on runtime data, use
 * {@link #post(String, byte[], int, byte[], int, byte[], int, byte[], int, int[])}. For a path that depends on
 * runtime data, use {@link #get(String, byte[], int, byte[], int, byte[], int, int[])} with a base URL. Small
 * protocol drivers that also need a runtime method or path can use {@link #requestHttps}.
 *
 * <p>The response body is written into {@code bodyBuffer} and the raw response headers into {@code headersBuffer}
 * (both caller-owned, fixed-size {@code byte[]}), each from index 0 and truncated to its given length. Each method
 * returns the number of body bytes written, or {@code -1} if the connection, TLS setup or certificate validation
 * failed. {@code statusAndHeadersLength} (a caller-owned {@code int[2]}, allocated once outside any loop like the
 * buffers) receives the HTTP status code at index 0 ({@code 0} if no status line arrived) and the number of bytes
 * written into {@code headersBuffer} at index 1. A request that doesn't complete within a fixed 5-second timeout is
 * abandoned and returns whatever was captured so far.
 */
public final class HttpClient {
    /** A reasonable default size for a caller's body/headers buffer, in bytes. */
    public static final int DEFAULT_RESPONSE_BUFFER_SIZE = 1024;

    private HttpClient() {
    }

    /** Sends a GET request to {@code url}. */
    public static native int get(String url,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /**
     * Sends a GET request to {@code baseUrl} (a scheme, host and optional port, with no path of its own) for the
     * path held in the first {@code pathLength} ASCII bytes of {@code pathBuffer}: for paths that depend on runtime
     * data, such as coordinates read from an earlier response (a runtime {@code String} is limited to 31 bytes, far
     * too short for a typical query string). {@code pathLength} must be less than 256; a longer or negative length
     * returns {@code -1} without connecting.
     */
    public static native int get(String baseUrl, byte[] pathBuffer, int pathLength,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** Sends a POST request to {@code url} with a compile-time body. */
    public static native int post(String url, String body,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /**
     * Sends a POST request to {@code url} with caller-owned request headers and a caller-owned JSON body. The first
     * {@code requestHeadersLength} bytes may contain complete HTTP header lines ending in {@code \r\n}, without the
     * final blank line; a {@code Content-Type} line there replaces the default {@code application/json}. The first
     * {@code requestBodyLength} bytes form the body. This overload is allocation-free and
     * is intended for small board-side clients whose authentication headers or JSON values are only known at runtime.
     */
    public static native int post(String url,
            byte[] requestHeadersBuffer, int requestHeadersLength,
            byte[] requestBodyBuffer, int requestBodyLength,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** Sends a bodyless DELETE request to {@code url}. */
    public static native int delete(String url,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /** Sends a PATCH request to {@code url} with a compile-time JSON body. */
    public static native int patch(String url, String body,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /**
     * Sends a safe, idempotent QUERY request to {@code url} with a compile-time JSON body, as defined by RFC 10008.
     */
    public static native int query(String url, String body,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);

    /**
     * Sends an HTTPS request whose host, path, request headers, and optional body are held in caller-owned storage.
     * This lower-level form supports small board-side protocol drivers that need runtime paths or authentication
     * headers without allocating strings. {@code host} is a DNS name without {@code https://}, a port, or a path. The
     * first {@code pathLength} bytes of {@code pathBuffer} are the request target. The first
     * {@code requestHeadersLength} bytes of {@code requestHeadersBuffer} may contain any number of complete HTTP
     * header lines, each ending in {@code \r\n}, without the final blank line. A zero length sends no custom headers.
     * A negative {@code requestBodyLength} sends no body; otherwise the first that many bytes of
     * {@code requestBodyBuffer} are sent as JSON.
     *
     * <p>The response contract is the same as {@link #get(String, byte[], int, byte[], int, int[])}. All storage is
     * caller-owned and the operation is allocation-free. Invalid negative lengths, a path of 256 bytes or more, or
     * a request-header block of 1024 bytes or more return {@code -1} without connecting.
     */
    public static native int requestHttps(String host, int port, String method,
            byte[] pathBuffer, int pathLength,
            byte[] requestHeadersBuffer, int requestHeadersLength,
            byte[] requestBodyBuffer, int requestBodyLength,
            byte[] bodyBuffer, int bodyBufferLength,
            byte[] headersBuffer, int headersBufferLength,
            int[] statusAndHeadersLength);
}
