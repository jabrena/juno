package io.github.jabrena.juno.jdk.net.httpserver;

import org.jspecify.annotations.Nullable;

/**
 * Request or response headers in Juno's bounded HTTP server subset. Response headers keep {@code Content-Type}, sent
 * by {@link HttpExchange#sendResponseHeaders(int, long)}; request headers are read from the current request by name.
 */
public final class Headers {
    private static final int VALUE_CAPACITY = 128;
    private final boolean request;
    private final byte @Nullable [] value;
    private String contentType = "text/plain";

    private Headers(boolean request) {
        this.request = request;
        this.value = request ? new byte[VALUE_CAPACITY] : null;
    }

    static Headers response() {
        return new Headers(false);
    }

    static Headers request() {
        return new Headers(true);
    }

    /** Sets a response header; the board sends only {@code Content-Type}, with any value. */
    public void set(String name, String value) {
        if (request || !name.equals("Content-Type")) return;
        contentType = value;
    }

    /**
     * The value of the request header called {@code name}, matched case-insensitively and truncated to 128 bytes, or
     * {@code null} when the request has none. Response headers always answer {@code null}.
     */
    public @Nullable String getFirst(String name) {
        byte[] buffer = value;
        if (!request || buffer == null) return null;
        int length = HttpServer.requestHeader(name, buffer, buffer.length);
        return length < 0 ? null : text(buffer, 0, length);
    }

    String contentType() {
        return contentType;
    }

    static native String text(byte[] bytes, int offset, int length);
}
