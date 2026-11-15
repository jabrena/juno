package io.github.jabrena.juno.jdk.net.http;

/**
 * The board-side {@code java.net.http.HttpResponse}: the status code and body of the last request a client sent. See
 * the package description for the subset.
 *
 * @param <T> the body type, which is {@code byte[]}
 */
public interface HttpResponse<T> {
    /** The HTTP status code. */
    int statusCode();

    /** The body; for {@link BodyHandlers#ofByteArray()} the client's fixed-capacity buffer. */
    T body();

    /**
     * Chooses how the body is delivered. Here it only names the body type: {@link BodyHandlers#ofByteArray()} is the one
     * handler.
     *
     * @param <T> the body type
     */
    interface BodyHandler<T> {
    }

    /** The available {@link BodyHandler}s. */
    final class BodyHandlers {
        private static final BodyHandler<byte[]> BYTE_ARRAY = new BodyHandler<byte[]>() {
        };

        private BodyHandlers() {
        }

        /** Delivers the body as bytes. Returns the same handler every time, so calling it in a loop allocates nothing. */
        public static BodyHandler<byte[]> ofByteArray() {
            return BYTE_ARRAY;
        }
    }
}
