package io.github.jabrena.juno.jdk.net.httpserver;

import java.io.IOException;

/** A JDK-shaped handler for one board-side HTTP request. */
@FunctionalInterface
public interface HttpHandler {
    void handle(HttpExchange exchange) throws IOException;
}
