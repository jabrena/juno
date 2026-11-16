package io.github.jabrena.juno.jdk.net.http;

/**
 * The board-side {@code java.net.http.HttpResponse}: the status code and body of the last request a client sent. See
 * the package description for the subset.
 *
 * @param <T> the body type: {@code byte[]} or {@code String}
 */
public interface HttpResponse<T> {
    /** The HTTP status code. */
    int statusCode();

    /**
     * The body: for {@link BodyHandlers#ofByteArray()} the client's fixed-capacity buffer, for
     * {@link BodyHandlers#ofString()} a {@code String} of the bytes received.
     */
    T body();

    /**
     * Chooses how the body is delivered: {@link BodyHandlers#ofByteArray()} or {@link BodyHandlers#ofString()}.
     *
     * @param <T> the body type
     */
    interface BodyHandler<T> {
        /**
         * Board-side only: the response for the request {@code client} has just received, with {@code statusCode}
         * and {@code length} body bytes in the client's buffer. The JDK's handler builds a subscriber instead; a
         * program never calls this.
         */
        HttpResponse<T> respond(HttpClient client, int statusCode, int length);
    }

    /**
     * The available {@link BodyHandler}s. Each one is created once, in its own class, so a program links only the
     * handlers it asks for: one that only reads bytes carries none of the {@code String} code.
     */
    final class BodyHandlers {
        private BodyHandlers() {
        }

        /** Delivers the body as bytes. Returns the same handler every time, so calling it in a loop allocates nothing. */
        public static BodyHandler<byte[]> ofByteArray() {
            return ByteArrayHandler.INSTANCE;
        }

        /**
         * Delivers the body as a {@code String} of the bytes received, so {@code body().length()} is the size received,
         * up to the client's buffer capacity. The bytes are not decoded: a runtime {@code String} holds bytes, so UTF-8
         * text stays UTF-8. Returns the same handler every time; each response allocates its {@code String}, and a
         * builder of the same size while it is made, so a full body takes about twice the buffer capacity of arena.
         * The previous response stays reachable until the program drops it: on the UNO WiFi Rev2 a loop of requests
         * needs {@code -Djuno.Xmx=3072}, where {@link #ofByteArray()} needs 1536.
         */
        public static BodyHandler<String> ofString() {
            return StringHandler.INSTANCE;
        }

        /** {@link #ofByteArray()}: the client's own reused response. */
        private static final class ByteArrayHandler implements BodyHandler<byte[]> {
            static final ByteArrayHandler INSTANCE = new ByteArrayHandler();

            @Override
            public HttpResponse<byte[]> respond(HttpClient client, int statusCode, int length) {
                return client.byteArrayResponse(statusCode, length);
            }
        }

        /** {@link #ofString()}: a new response holding the body as a {@code String}. */
        private static final class StringHandler implements BodyHandler<String> {
            static final StringHandler INSTANCE = new StringHandler();

            @Override
            public HttpResponse<String> respond(HttpClient client, int statusCode, int length) {
                return client.stringResponse(statusCode, length);
            }
        }
    }
}
