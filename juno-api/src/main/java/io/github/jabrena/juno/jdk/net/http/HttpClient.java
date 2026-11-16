package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.hardware.internal.NativeHttpFeatures;
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
 *
 * <p>As in the JDK since 21, a client is {@link AutoCloseable}, so it can be the resource of a
 * {@code try}-with-resources statement. Every request runs to completion in the thread that sends it, so a client
 * that is shut down has no request left to wait for: {@link #isTerminated()} is true at once, and a later
 * {@link #send} throws {@link IOException}.
 */
public final class HttpClient implements AutoCloseable {
    /**
     * The size in bytes of the buffer {@link HttpResponse.BodyHandlers#ofByteArray()} receives a body into: a longer
     * body is truncated to it. {@link HttpResponse.BodyHandlers#ofString()} has no such limit.
     */
    public static final int BODY_CAPACITY = 384;
    /** The response-header buffer's size in bytes, in a program that reads headers or follows redirects. */
    public static final int RESPONSE_HEADERS_CAPACITY = 512;
    /** How many redirects one {@link #send} follows at most, as a JDK client by default. */
    public static final int MAX_REDIRECTS = 5;
    /** How long a request waits when neither it nor its client sets a timeout, in milliseconds. */
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;
    private static final int UNREAD_HEADERS_CAPACITY = 16;

    private final byte[] headers;
    /** The status, the header bytes received, and whether a String body did not fit in the arena. */
    private final int[] statusAndHeadersLength = new int[3];
    /** The response headers, in a program that reads them or follows redirects; {@code null} in any other. */
    private final @Nullable HttpHeaders responseHeaders;
    /** The byte-array body buffer and its reused response, made on the first {@code ofByteArray()} response. */
    private @Nullable ByteArrayResponse response;
    /** The body of the last {@code ofString()} request. */
    private @Nullable String textBody;
    /** The redirect policy, or {@code null} for {@link Redirect#NEVER}, so a program without redirects needs no enum. */
    private final @Nullable Redirect redirect;
    private final int connectTimeoutMillis;
    private boolean shutDown;

    private HttpClient(@Nullable Redirect redirect, int connectTimeoutMillis) {
        // Decided when the program is linked: a full buffer only for a program that reads headers or follows redirects.
        if (NativeHttpFeatures.responseHeaders()) {
            headers = new byte[RESPONSE_HEADERS_CAPACITY];
            responseHeaders = new HttpHeaders(headers, statusAndHeadersLength);
        } else {
            headers = new byte[UNREAD_HEADERS_CAPACITY];
            responseHeaders = null;
        }
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
     * @throws IOException when the client is shut down, the request could not be sent, or a redirect could not be
     *                     followed
     * @throws InterruptedException never on the board; declared because the JDK method declares it
     */
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        if (shutDown) {
            throw new IOException("The client is shut down");
        }
        URI target = request.uri();
        String method = request.method();
        byte[] bodyBytes = request.bodyBytes();
        int bodyLength = request.bodyLength();
        boolean text = responseBodyHandler.receivesText();
        int length = exchange(request, target, method, bodyBytes, bodyLength, text);
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
            length = exchange(request, target, method, bodyBytes, bodyLength, text);
        }
        return responseBodyHandler.respond(this, statusCode(), length, target);
    }

    /** Shuts the client down: a later {@link #send} throws {@link IOException}. Shutting it down again does nothing. */
    public void shutdown() {
        shutDown = true;
    }

    /**
     * Shuts the client down, as {@link #shutdown()} does: there is no request in flight to interrupt, since every
     * request runs to completion in the thread that sends it.
     */
    public void shutdownNow() {
        shutDown = true;
    }

    /**
     * Whether the client is shut down, waiting for nothing: every request runs to completion in the thread that
     * sends it, so a shut-down client has none left.
     *
     * @throws InterruptedException never on the board; declared because the JDK method declares it
     */
    public boolean awaitTermination(Duration duration) throws InterruptedException {
        return shutDown;
    }

    /** Whether the client is shut down, so it has no request left. */
    public boolean isTerminated() {
        return shutDown;
    }

    /** Shuts the client down, as {@link #shutdown()} does; the {@code try}-with-resources statement calls it. */
    @Override
    public void close() {
        shutDown = true;
    }

    /** The client's reused byte-array response, its buffer zero-filled past the {@code length} bytes received. */
    HttpResponse<byte[]> byteArrayResponse(int statusCode, int length, URI uri) {
        ByteArrayResponse reused = byteArrayState();
        byte[] body = reused.body;
        for (int i = length; i < body.length; i++) {
            body[i] = 0;
        }
        reused.statusCode = statusCode;
        reused.uri = uri;
        return reused;
    }

    /** A new response holding the body the last {@code ofString()} request received. */
    HttpResponse<String> stringResponse(int statusCode, URI uri) {
        String received = textBody;
        return new StringResponse(statusCode, received == null ? "" : received, responseHeaders, uri);
    }

    /** The reused byte-array response and its body buffer, made the first time a program needs them. */
    private ByteArrayResponse byteArrayState() {
        ByteArrayResponse reused = response;
        if (reused == null) {
            reused = new ByteArrayResponse(new byte[BODY_CAPACITY], responseHeaders);
            response = reused;
        }
        return reused;
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

    /**
     * Sends one request and returns the body bytes received into the byte-array buffer; with {@code text}, the body
     * is kept in {@link #textBody} instead, at its own size.
     */
    private int exchange(HttpRequest request, URI uri, String method, byte[] bodyBytes, int bodyLength, boolean text)
            throws IOException {
        // Decided when the program is linked: a program that sets no timeout keeps the board's own.
        if (NativeHttpFeatures.timeouts()) {
            io.github.jabrena.juno.hardware.net.http.HttpClient.setTimeout(timeoutMillis(request));
        }
        // Never null: HttpRequest.Builder.uri and redirectTarget only accept a URI with a host.
        String host = uri.getHost();
        if (host == null) {
            throw new IOException("The URI has no host");
        }
        String target = uri.requestTarget();
        // Decided when the program is linked: only a program that calls HttpRequest.Builder.method can send a CONNECT.
        if (NativeHttpFeatures.customMethods() && method.equals("CONNECT")) {
            // A CONNECT names its tunnel's end, host:port, rather than a path.
            target = uri.authorityTarget();
        }
        int length;
        if (text) {
            textBody = null;
            String received = BoardRequests.receiveText(request, uri, host, method, target, bodyBytes, bodyLength,
                    headers, statusAndHeadersLength);
            textBody = received;
            length = received.length();
        } else {
            length = BoardRequests.receiveBytes(request, uri, host, method, target, bodyBytes, bodyLength,
                    byteArrayState().body, headers, statusAndHeadersLength);
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
     * Where the redirect just received points, its {@code Location} resolved against {@code current}, or {@code null}
     * when it is not followed: it has no {@code Location}, or it leaves {@code https} for {@code http} under
     * {@link Redirect#NORMAL}.
     */
    private @Nullable URI redirectTarget(URI current) throws IOException {
        HttpHeaders received = responseHeaders;
        if (received == null) {
            return null;
        }
        Optional<String> location = received.firstValue("location");
        if (location.isEmpty()) {
            return null;
        }
        URI next;
        try {
            next = current.resolve(location.get());
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot follow the redirect to " + location.get());
        }
        if (!next.isHttp() || next.getHost() == null) {
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
