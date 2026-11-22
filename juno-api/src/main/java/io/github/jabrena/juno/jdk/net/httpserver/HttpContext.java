package io.github.jabrena.juno.jdk.net.httpserver;

import org.jspecify.annotations.Nullable;

/** A registered path prefix and its request handler. */
public final class HttpContext {
    private final String path;
    private final HttpHandler handler;
    private @Nullable HttpContext next;

    HttpContext(String path, HttpHandler handler, @Nullable HttpContext next) {
        this.path = path;
        this.handler = handler;
        this.next = next;
    }

    String path() {
        return path;
    }

    HttpHandler handler() {
        return handler;
    }

    @Nullable HttpContext next() {
        return next;
    }

    void next(HttpContext context) {
        next = context;
    }
}
