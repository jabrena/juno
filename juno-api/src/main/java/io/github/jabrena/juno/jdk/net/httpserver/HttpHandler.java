package io.github.jabrena.juno.jdk.net.httpserver;

import java.io.IOException;

/** A JDK-shaped handler for one board-side HTTP request. */
@FunctionalInterface
public interface HttpHandler {
    /** Answers one request; called on the server's dispatch thread. */
    void handle(HttpExchange exchange) throws IOException;
}
