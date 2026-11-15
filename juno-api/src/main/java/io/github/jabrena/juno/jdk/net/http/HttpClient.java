package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.hardware.internal.NativeHttpSchemes;
import io.github.jabrena.juno.jdk.net.URI;

import java.io.IOException;

/**
 * The board-side {@code java.net.http.HttpClient}: sends a {@link HttpRequest} through the board's own
 * {@link io.github.jabrena.juno.hardware.net.http.HttpClient} and answers with one reused {@link HttpResponse}. See the
 * package description for the subset.
 */
public final class HttpClient {
    /** The body buffer's size in bytes: a longer body is truncated to it. */
    public static final int BODY_CAPACITY = 384;
    private static final int HEADERS_CAPACITY = 16;
    private static final byte[] NO_REQUEST_BYTES = new byte[1];

    private final byte[] body = new byte[BODY_CAPACITY];
    private final byte[] headers = new byte[HEADERS_CAPACITY];
    private final int[] statusAndHeadersLength = new int[2];
    private final ByteArrayResponse response = new ByteArrayResponse(body);

    private HttpClient() {
    }

    /** A client with the default settings. */
    public static HttpClient newHttpClient() {
        return new HttpClient();
    }

    /** A builder for a client. */
    public static Builder newBuilder() {
        return new ClientBuilder();
    }

    /**
     * Sends {@code request} and waits for the reply. The response is the client's own object, refilled by every call,
     * so read it before the next request.
     *
     * @throws IOException when the request could not be sent or no reply arrived
     * @throws InterruptedException never on the board; declared because the JDK method declares it
     */
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        URI uri = request.uri();
        int length;
        // Each scheme asks its link-time query first, so a program that only ever sends to one scheme links only
        // that scheme's request: the other branch, and its shim, are dropped when the program is linked.
        if (uri.isSecure()) {
            if (!NativeHttpSchemes.https()) {
                throw new IOException("This program sends no https request");
            }
            length = io.github.jabrena.juno.hardware.net.http.HttpClient.requestHttps(uri.getHost(), uri.connectPort(),
                    "GET", uri.pathBytes(), uri.pathLength(), NO_REQUEST_BYTES, 0, NO_REQUEST_BYTES, -1, body,
                    body.length, headers, headers.length, statusAndHeadersLength);
        } else {
            if (!NativeHttpSchemes.http()) {
                throw new IOException("This program sends no http request");
            }
            length = io.github.jabrena.juno.hardware.net.http.HttpClient.requestHttp(uri.getHost(), uri.connectPort(),
                    "GET", uri.pathBytes(), uri.pathLength(), NO_REQUEST_BYTES, 0, NO_REQUEST_BYTES, -1, body,
                    body.length, headers, headers.length, statusAndHeadersLength);
        }
        if (length < 0) {
            throw new IOException("The HTTP request failed");
        }
        for (int i = length; i < body.length; i++) {
            body[i] = 0;
        }
        response.statusCode = statusAndHeadersLength[0];
        return (HttpResponse<T>) response;
    }

    /** The HTTP version a client asks for; the board always speaks HTTP/1.1. */
    public enum Version {
        HTTP_1_1,
        HTTP_2
    }

    /** Builds a {@link HttpClient}. */
    public interface Builder {
        /** Sets the preferred version; the board sends HTTP/1.1 either way, as a JDK client falls back to it. */
        Builder version(Version version);

        /** Builds the client, with its response and buffers. */
        HttpClient build();
    }

    private static final class ClientBuilder implements Builder {
        @Override
        public Builder version(Version version) {
            return this;
        }

        @Override
        public HttpClient build() {
            return new HttpClient();
        }
    }

    /** The one response a client owns: its status, and its body buffer. */
    private static final class ByteArrayResponse implements HttpResponse<byte[]> {
        private final byte[] body;
        private int statusCode;

        ByteArrayResponse(byte[] body) {
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public byte[] body() {
            return body;
        }
    }
}
