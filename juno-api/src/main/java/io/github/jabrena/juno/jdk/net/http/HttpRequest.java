package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.jdk.net.URI;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * The board-side {@code java.net.http.HttpRequest}: an immutable request for one {@link URI}, built once and sent as
 * often as needed. See the package description for the subset.
 *
 * <p>Its headers are kept as the bytes the board sends, at most {@value #HEADERS_CAPACITY} of them, so a request
 * without headers allocates no header buffer. As in the JDK, {@code Connection}, {@code Content-Length},
 * {@code Expect}, {@code Host} and {@code Upgrade} are set by the client and rejected here.
 */
public final class HttpRequest {
    /** The most bytes of header lines ({@code Name: value\r\n} each) a request can carry. */
    public static final int HEADERS_CAPACITY = 512;
    private static final byte[] NO_HEADERS = new byte[1];
    private static final byte[] NO_BODY = new byte[1];
    /** The body length of a request without a body: no body and no {@code Content-Length} are sent. */
    static final int NO_BODY_LENGTH = -1;
    private static final String GET = "GET";

    private final URI uri;
    private final String method;
    private final byte[] bodyBytes;
    private final int bodyLength;
    private final byte[] headerBytes;
    private final int headersLength;
    private final int timeoutMillis;

    private HttpRequest(RequestBuilder builder, URI uri) {
        this.uri = uri;
        this.method = builder.method;
        this.bodyBytes = builder.bodyBytes;
        this.bodyLength = builder.bodyLength;
        this.headerBytes = builder.headerBytes;
        this.headersLength = builder.headersLength;
        this.timeoutMillis = builder.timeoutMillis;
    }

    /** A builder with no URI yet. */
    public static Builder newBuilder() {
        return new RequestBuilder();
    }

    /** A builder for {@code uri}. */
    public static Builder newBuilder(URI uri) {
        return new RequestBuilder().uri(uri);
    }

    /** The request method, such as {@code "GET"} or {@code "POST"}. */
    public String method() {
        return method;
    }

    /** The URI the request is sent to. */
    public URI uri() {
        return uri;
    }

    /** The header lines to send, valid up to {@link #headersLength()}. Board-side only. */
    byte[] headerBytes() {
        return headerBytes;
    }

    /** How many bytes of {@link #headerBytes()} hold header lines. Board-side only. */
    int headersLength() {
        return headersLength;
    }

    /** The body bytes to send, valid up to {@link #bodyLength()}. Board-side only. */
    byte[] bodyBytes() {
        return bodyBytes;
    }

    /** How many body bytes to send, or {@link #NO_BODY_LENGTH} for a request without a body. Board-side only. */
    int bodyLength() {
        return bodyLength;
    }

    /** The timeout in milliseconds, or {@code 0} when the request sets none. Board-side only. */
    int timeoutMillis() {
        return timeoutMillis;
    }

    /** Builds a {@link HttpRequest}. */
    public interface Builder {
        /**
         * Sets the URI the request is sent to.
         *
         * @throws IllegalArgumentException if its scheme is not {@code http} or {@code https}, or it has no host
         */
        Builder uri(URI uri);

        /**
         * Adds a header. A name may be added more than once.
         *
         * @throws IllegalArgumentException if the name is restricted or invalid, the value holds a line break, or the
         *                                  request's headers would pass {@value #HEADERS_CAPACITY} bytes
         */
        Builder header(String name, String value);

        /**
         * Adds each name and value pair, as {@link #header(String, String)} does.
         *
         * @throws IllegalArgumentException if a name has no value, or as {@link #header(String, String)}
         */
        Builder headers(String... namesAndValues);

        /** Sets a header, replacing every header of that name added before. */
        Builder setHeader(String name, String value);

        /**
         * Sets how long the client waits for the response.
         *
         * @throws IllegalArgumentException if {@code duration} is not positive
         */
        Builder timeout(Duration duration);

        /** Makes the request a {@code GET}, with no body: the default. */
        Builder GET();

        /** Makes the request a {@code POST} of {@code body}. */
        Builder POST(BodyPublisher body);

        /** Makes the request a {@code PUT} of {@code body}. */
        Builder PUT(BodyPublisher body);

        /** Makes the request a {@code DELETE}, with no body. */
        Builder DELETE();

        /** Makes the request a {@code HEAD}, with no body: the response has a status and headers but no body. */
        Builder HEAD();

        /**
         * Sets the method and body, for a method such as {@code PATCH} or {@code OPTIONS}. Unlike the JDK, the board
         * also sends {@code CONNECT}: its request target is the URI's {@code host:port}, and the response has a status
         * and headers but no body. The board closes the connection after it, so the tunnel cannot be used.
         *
         * @throws IllegalArgumentException if {@code method} is empty or not a token
         */
        Builder method(String method, BodyPublisher body);

        /**
         * Builds the request. The builder can go on being used: a later header does not change this request.
         *
         * @throws IllegalStateException if no URI was set
         */
        HttpRequest build();
    }

    /**
     * A request body. Board-side, it is the bytes to send; the JDK's publisher streams them instead, which a program
     * does not see.
     */
    public interface BodyPublisher {
        /** The body's length in bytes. */
        long contentLength();

        /** The bytes to send, valid up to {@link #contentLength()}. Board-side only: a program never calls this. */
        byte[] bodyBytes();
    }

    /** The available {@link BodyPublisher}s. */
    public static final class BodyPublishers {
        /** The longest body {@link #ofString(String)} accepts, in bytes. */
        public static final int STRING_CAPACITY = 512;
        private static final byte[] EMPTY = new byte[1];

        private BodyPublishers() {
        }

        /**
         * The characters of {@code body}, one byte each: a runtime {@code String} holds bytes, so UTF-8 text is sent
         * as UTF-8.
         *
         * @throws IllegalArgumentException if {@code body} is longer than {@value #STRING_CAPACITY} bytes
         */
        public static BodyPublisher ofString(String body) {
            if (body.length() > STRING_CAPACITY) {
                throw new IllegalArgumentException("A String body holds at most 512 bytes");
            }
            byte[] bytes = new byte[STRING_CAPACITY];
            for (int i = 0; i < body.length(); i++) {
                bytes[i] = (byte) body.charAt(i);
            }
            return new ByteArrayPublisher(bytes, body.length());
        }

        /** The whole of {@code body}, sent as it is when the request is sent. */
        public static BodyPublisher ofByteArray(byte[] body) {
            return new ByteArrayPublisher(body, body.length);
        }

        /**
         * The first {@code length} bytes of {@code body}, sent as they are when the request is sent: a body encoded
         * into a buffer allocated once. Unlike the JDK, {@code offset} must be {@code 0}: the board sends straight
         * from the start of the array and cannot make a copy of a runtime size.
         *
         * @throws IndexOutOfBoundsException if {@code length} is negative or passes the end of {@code body}
         * @throws IllegalArgumentException if {@code offset} is not {@code 0}
         */
        public static BodyPublisher ofByteArray(byte[] body, int offset, int length) {
            if (length < 0 || offset < 0 || offset + length > body.length) {
                throw new IndexOutOfBoundsException("The body range is outside the array");
            }
            if (offset != 0) {
                throw new IllegalArgumentException("A body range must start at offset 0 on the board");
            }
            return new ByteArrayPublisher(body, length);
        }

        /** No body: a {@code POST} or {@code PUT} of nothing, with {@code Content-Length: 0}. */
        public static BodyPublisher noBody() {
            return new ByteArrayPublisher(EMPTY, 0);
        }
    }

    private static final class ByteArrayPublisher implements BodyPublisher {
        private final byte[] bytes;
        private final int length;

        ByteArrayPublisher(byte[] bytes, int length) {
            this.bytes = bytes;
            this.length = length;
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public byte[] bodyBytes() {
            return bytes;
        }
    }

    private static final class RequestBuilder implements Builder {
        private @Nullable URI target;
        private String method = GET;
        private byte[] bodyBytes = NO_BODY;
        private int bodyLength = NO_BODY_LENGTH;
        private byte[] headerBytes = NO_HEADERS;
        private int headersLength;
        // Set when a built request shares headerBytes, so the next header copies them first.
        private boolean headersShared;
        private int timeoutMillis;

        @Override
        public Builder uri(URI uri) {
            if (!uri.isHttp()) {
                throw new IllegalArgumentException("Unsupported URI scheme: " + uri.toString());
            }
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("The URI has no host: " + uri.toString());
            }
            target = uri;
            return this;
        }

        @Override
        public Builder header(String name, String value) {
            RequestHeaders.check(name, value);
            appendHeader(name, value);
            return this;
        }

        @Override
        public Builder headers(String... namesAndValues) {
            if (namesAndValues.length % 2 != 0) {
                throw new IllegalArgumentException("Each header name needs a value");
            }
            for (int i = 0; i < namesAndValues.length; i += 2) {
                header(namesAndValues[i], namesAndValues[i + 1]);
            }
            return this;
        }

        @Override
        public Builder setHeader(String name, String value) {
            RequestHeaders.check(name, value);
            ownHeaders();
            headersLength = RequestHeaders.remove(headerBytes, headersLength, name);
            appendHeader(name, value);
            return this;
        }

        @Override
        public Builder timeout(Duration duration) {
            long millis = duration.toMillis();
            if (millis <= 0) {
                throw new IllegalArgumentException("The timeout must be positive");
            }
            timeoutMillis = millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
            return this;
        }

        @Override
        public Builder GET() {
            return withoutBody(GET);
        }

        @Override
        public Builder POST(BodyPublisher publisher) {
            return method("POST", publisher);
        }

        @Override
        public Builder PUT(BodyPublisher publisher) {
            return method("PUT", publisher);
        }

        @Override
        public Builder DELETE() {
            return withoutBody("DELETE");
        }

        @Override
        public Builder HEAD() {
            return withoutBody("HEAD");
        }

        @Override
        public Builder method(String name, BodyPublisher publisher) {
            RequestHeaders.checkMethod(name);
            method = name;
            // Read here, not when the request is sent: a program that never calls this, or POST or PUT, then needs no
            // body publisher at all, and the client calls none.
            bodyBytes = publisher.bodyBytes();
            bodyLength = (int) publisher.contentLength();
            return this;
        }

        private Builder withoutBody(String name) {
            method = name;
            bodyBytes = NO_BODY;
            bodyLength = NO_BODY_LENGTH;
            return this;
        }

        @Override
        public HttpRequest build() {
            URI chosen = target;
            if (chosen == null) {
                throw new IllegalStateException("No URI was set");
            }
            headersShared = headersLength > 0;
            return new HttpRequest(this, chosen);
        }

        private void appendHeader(String name, String value) {
            ownHeaders();
            headersLength = RequestHeaders.append(headerBytes, headersLength, name, value);
        }

        /** Gives the builder a header buffer of its own: a first one, or a copy of the one a built request shares. */
        private void ownHeaders() {
            if (headerBytes == NO_HEADERS || headersShared) {
                byte[] own = new byte[HEADERS_CAPACITY];
                for (int i = 0; i < headersLength; i++) {
                    own[i] = headerBytes[i];
                }
                headerBytes = own;
                headersShared = false;
            }
        }
    }
}
