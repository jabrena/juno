package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.hardware.internal.NativeHttpFeatures;
import io.github.jabrena.juno.hardware.internal.NativeHttpSchemes;
import io.github.jabrena.juno.jdk.net.URI;
import io.github.jabrena.juno.jdk.util.Optional;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;

/**
 * The board-side {@code java.net.http.HttpClient}: sends a {@link HttpRequest} through the board's own
 * {@link io.github.jabrena.juno.hardware.net.http.HttpClient}. See the package description for the subset.
 *
 * <p>What a program never uses is not linked: the request code of a scheme none of its URIs name, the redirect code
 * when it never calls {@code followRedirects}, and a full response-header buffer when it never reads
 * {@code headers()} or follows redirects.
 */
public final class HttpClient {
    /** The body buffer's size in bytes: a longer body is truncated to it. */
    public static final int BODY_CAPACITY = 384;
    /** The response-header buffer's size in bytes, in a program that reads headers or follows redirects. */
    public static final int RESPONSE_HEADERS_CAPACITY = 512;
    /** How many redirects one {@link #send} follows at most, as a JDK client by default. */
    public static final int MAX_REDIRECTS = 5;
    /** How long a request waits when neither it nor its client sets a timeout, in milliseconds. */
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;
    private static final int UNREAD_HEADERS_CAPACITY = 16;

    private final byte[] body = new byte[BODY_CAPACITY];
    private final byte[] headers;
    private final int[] statusAndHeadersLength = new int[2];
    /** The response headers, in a program that reads them or follows redirects; {@code null} in any other. */
    private final @Nullable HttpHeaders responseHeaders;
    private final ByteArrayResponse response;
    /** The redirect policy, or {@code null} for {@link Redirect#NEVER}, so a program without redirects needs no enum. */
    private final @Nullable Redirect redirect;
    private final int connectTimeoutMillis;

    private HttpClient(@Nullable Redirect redirect, int connectTimeoutMillis) {
        // Decided when the program is linked: a full buffer only for a program that reads headers or follows redirects.
        if (NativeHttpFeatures.responseHeaders()) {
            headers = new byte[RESPONSE_HEADERS_CAPACITY];
            responseHeaders = new HttpHeaders(headers, statusAndHeadersLength);
        } else {
            headers = new byte[UNREAD_HEADERS_CAPACITY];
            responseHeaders = null;
        }
        response = new ByteArrayResponse(body, responseHeaders);
        this.redirect = redirect;
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    /** A client with the default settings: no redirects, and the default timeout. */
    public static HttpClient newHttpClient() {
        return new HttpClient(null, 0);
    }

    /** A builder for a client. */
    public static Builder newBuilder() {
        return new ClientBuilder();
    }

    /** The client's redirect policy. */
    public Redirect followRedirects() {
        return redirect == null ? Redirect.NEVER : redirect;
    }

    /**
     * Sends {@code request} and waits for the reply, following redirects as the client's policy says. With
     * {@link HttpResponse.BodyHandlers#ofByteArray()} the response is the client's own object, refilled by every call,
     * so read it before the next request; with {@link HttpResponse.BodyHandlers#ofString()} it is a new object holding
     * its own {@code String}. Either way its headers are read from the client's buffer, refilled by every call.
     *
     * @throws HttpTimeoutException when no response arrived within the timeout
     * @throws IOException when the request could not be sent, or a redirect could not be followed
     * @throws InterruptedException never on the board; declared because the JDK method declares it
     */
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        URI target = request.uri();
        String method = request.method();
        byte[] bodyBytes = request.bodyBytes();
        int bodyLength = request.bodyLength();
        int length = exchange(request, target, method, bodyBytes, bodyLength);
        int hops = 0;
        // Decided when the program is linked: a program that never calls followRedirects links none of this loop.
        while (NativeHttpFeatures.redirects() && redirect != null && redirect != Redirect.NEVER
                && isRedirect(statusCode())) {
            URI next = redirectTarget(target);
            if (next == null) {
                break;
            }
            if (hops == MAX_REDIRECTS) {
                throw new IOException("Too many redirects");
            }
            if (changesToGet(statusCode(), method)) {
                method = "GET";
                bodyLength = HttpRequest.NO_BODY_LENGTH;
            }
            target = next;
            hops++;
            length = exchange(request, target, method, bodyBytes, bodyLength);
        }
        return responseBodyHandler.respond(this, statusCode(), length, target);
    }

    /** The client's reused byte-array response, its buffer zero-filled past the {@code length} bytes received. */
    HttpResponse<byte[]> byteArrayResponse(int statusCode, int length, URI uri) {
        for (int i = length; i < body.length; i++) {
            body[i] = 0;
        }
        response.statusCode = statusCode;
        response.uri = uri;
        return response;
    }

    /** A new response holding the {@code length} bytes received as a {@code String}. */
    HttpResponse<String> stringResponse(int statusCode, int length, URI uri) {
        return new StringResponse(statusCode, text(body, length), response.headers, uri);
    }

    /** The first {@code length} of {@code bytes} as a {@code String}, one character per byte. */
    static String text(byte[] bytes, int length) {
        // Sized to the body received, not to the buffer, so a short body costs the arena only its own bytes.
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append((char) (bytes[i] & 0xFF));
        }
        return text.toString();
    }

    /** Whether {@code status} is a redirect a client follows. */
    static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /** Whether following a {@code status} redirect of a {@code method} request turns it into a {@code GET}. */
    static boolean changesToGet(int status, String method) {
        if (status == 303) {
            return !method.equals("HEAD");
        }
        return (status == 301 || status == 302) && method.equals("POST");
    }

    private int statusCode() {
        return statusAndHeadersLength[0];
    }

    /** Sends one request and returns the body bytes received. */
    private int exchange(HttpRequest request, URI uri, String method, byte[] bodyBytes, int bodyLength)
            throws IOException {
        // Decided when the program is linked: a program that sets no timeout keeps the board's own.
        if (NativeHttpFeatures.timeouts()) {
            io.github.jabrena.juno.hardware.net.http.HttpClient.setTimeout(timeoutMillis(request));
        }
        byte[] target = uri.pathBytes();
        int targetLength = uri.pathLength();
        // Decided when the program is linked: only a program that calls HttpRequest.Builder.method can send a CONNECT.
        if (NativeHttpFeatures.customMethods() && method.equals("CONNECT")) {
            // A CONNECT names its tunnel's end, host:port, rather than a path.
            target = new byte[URI.MAX_PATH_LENGTH];
            targetLength = uri.writeAuthority(target);
        }
        int length;
        // Each scheme asks its link-time query first, so a program that only ever sends to one scheme links only
        // that scheme's request: the other branch, and its shim, are dropped when the program is linked.
        if (uri.isSecure()) {
            if (!NativeHttpSchemes.https()) {
                throw new IOException("This program links no https request: name an https URI in it to send one");
            }
            length = io.github.jabrena.juno.hardware.net.http.HttpClient.requestHttps(uri.getHost(), uri.connectPort(),
                    method, target, targetLength, request.headerBytes(), request.headersLength(),
                    bodyBytes, bodyLength, body, body.length, headers, headers.length, statusAndHeadersLength);
        } else {
            if (!NativeHttpSchemes.http()) {
                throw new IOException("This program links no http request: name an http URI in it to send one");
            }
            length = io.github.jabrena.juno.hardware.net.http.HttpClient.requestHttp(uri.getHost(), uri.connectPort(),
                    method, target, targetLength, request.headerBytes(), request.headersLength(),
                    bodyBytes, bodyLength, body, body.length, headers, headers.length, statusAndHeadersLength);
        }
        if (length < 0) {
            throw new IOException("The HTTP request failed");
        }
        // Decided when the program is linked: in a program that sets a timeout, no response within it throws; any
        // other program answers status 0, as the board's own client does.
        if (NativeHttpFeatures.timeouts() && statusCode() == 0) {
            throw new HttpTimeoutException("No response within the timeout");
        }
        return length;
    }

    /** The request's timeout, else the client's connect timeout, else the board default. */
    private int timeoutMillis(HttpRequest request) {
        if (request.timeoutMillis() > 0) {
            return request.timeoutMillis();
        }
        return connectTimeoutMillis > 0 ? connectTimeoutMillis : DEFAULT_TIMEOUT_MILLIS;
    }

    /**
     * Where the redirect just received points, or {@code null} when it is not followed: it has no {@code Location}, or
     * it leaves {@code https} for {@code http} under {@link Redirect#NORMAL}.
     */
    private @Nullable URI redirectTarget(URI current) throws IOException {
        Optional<String> location = response.headers().firstValue("location");
        if (location.isEmpty()) {
            return null;
        }
        URI next;
        try {
            next = current.resolveRedirect(location.get());
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot follow the redirect to " + location.get());
        }
        if (redirect == Redirect.NORMAL && current.isSecure() && !next.isSecure()) {
            return null;
        }
        return next;
    }

    /** The HTTP version a client asks for; the board always speaks HTTP/1.1. */
    public enum Version {
        /** HTTP/1.1, which the board speaks. */
        HTTP_1_1,
        /** HTTP/2, which a JDK client prefers; the board sends HTTP/1.1 instead, as a JDK client falls back to it. */
        HTTP_2
    }

    /** Whether a client follows the redirects it receives. */
    public enum Redirect {
        /** Never follow a redirect: the default. */
        NEVER,
        /** Always follow a redirect. */
        ALWAYS,
        /** Follow a redirect, except from {@code https} to {@code http}. */
        NORMAL
    }

    /** Builds a {@link HttpClient}. */
    public interface Builder {
        /** Sets the preferred version; the board sends HTTP/1.1 either way, as a JDK client falls back to it. */
        Builder version(Version version);

        /**
         * Sets the redirect policy. A redirect to a scheme none of the program's URIs name cannot be followed: only
         * those schemes' requests are linked.
         */
        Builder followRedirects(Redirect policy);

        /**
         * Sets how long a request waits when it sets no timeout of its own. The board has one timeout for the whole
         * exchange, so this is not only the connect time.
         *
         * @throws IllegalArgumentException if {@code duration} is not positive
         */
        Builder connectTimeout(Duration duration);

        /** Builds the client, with its response and buffers. */
        HttpClient build();
    }

    private static final class ClientBuilder implements Builder {
        private @Nullable Redirect redirect;
        private int connectTimeoutMillis;

        @Override
        public Builder version(Version version) {
            return this;
        }

        @Override
        public Builder followRedirects(Redirect policy) {
            redirect = policy;
            return this;
        }

        @Override
        public Builder connectTimeout(Duration duration) {
            long millis = duration.toMillis();
            if (millis <= 0) {
                throw new IllegalArgumentException("The timeout must be positive");
            }
            connectTimeoutMillis = millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
            return this;
        }

        @Override
        public HttpClient build() {
            return new HttpClient(redirect, connectTimeoutMillis);
        }
    }

    /** The one response a client owns: its status, its URI, and its body buffer. */
    private static final class ByteArrayResponse implements HttpResponse<byte[]> {
        private final byte[] body;
        private final @Nullable HttpHeaders headers;
        private int statusCode;
        private @Nullable URI uri;

        ByteArrayResponse(byte[] body, @Nullable HttpHeaders headers) {
            this.body = body;
            this.headers = headers;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public byte[] body() {
            return body;
        }

        @Override
        public HttpHeaders headers() {
            // Never null when this can be called: a program that calls headers() links the full header buffer.
            HttpHeaders read = headers;
            if (read == null) {
                throw new IllegalStateException("This program links no response headers");
            }
            return read;
        }

        @Override
        public URI uri() {
            URI sent = uri;
            if (sent == null) {
                throw new IllegalStateException("No request was sent");
            }
            return sent;
        }
    }

    /** A response to {@link HttpResponse.BodyHandlers#ofString()}: its status, its URI, and its body as a String. */
    private static final class StringResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;
        private final @Nullable HttpHeaders headers;
        private final URI uri;

        StringResponse(int statusCode, String body, @Nullable HttpHeaders headers, URI uri) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
            this.uri = uri;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public HttpHeaders headers() {
            // Never null when this can be called: a program that calls headers() links the full header buffer.
            HttpHeaders read = headers;
            if (read == null) {
                throw new IllegalStateException("This program links no response headers");
            }
            return read;
        }

        @Override
        public URI uri() {
            return uri;
        }
    }
}
