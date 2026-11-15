package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.jdk.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * The board-side {@code java.net.http.HttpRequest}: an immutable {@code GET} request for one {@link URI}, built once
 * and sent as often as needed. See the package description for the subset.
 */
public final class HttpRequest {
    private final URI uri;

    private HttpRequest(URI uri) {
        this.uri = uri;
    }

    /** A builder with no URI yet. */
    public static Builder newBuilder() {
        return new RequestBuilder();
    }

    /** A builder for {@code uri}. */
    public static Builder newBuilder(URI uri) {
        return new RequestBuilder().uri(uri);
    }

    /** Always {@code "GET"}. */
    public String method() {
        return "GET";
    }

    /** The URI the request is sent to. */
    public URI uri() {
        return uri;
    }

    /** Builds a {@link HttpRequest}. */
    public interface Builder {
        /** Sets the URI the request is sent to. */
        Builder uri(URI uri);

        /** Makes the request a {@code GET}, which is what every request here is. */
        Builder GET();

        /**
         * Builds the request.
         *
         * @throws IllegalStateException if no URI was set
         */
        HttpRequest build();
    }

    private static final class RequestBuilder implements Builder {
        private @Nullable URI target;

        @Override
        public Builder uri(URI uri) {
            target = uri;
            return this;
        }

        @Override
        public Builder GET() {
            return this;
        }

        @Override
        public HttpRequest build() {
            URI chosen = target;
            if (chosen == null) {
                throw new IllegalStateException("No URI was set");
            }
            return new HttpRequest(chosen);
        }
    }
}
