package io.github.jabrena.juno.jdk.net.httpserver;

import io.github.jabrena.juno.jdk.net.InetSocketAddress;
import io.github.jabrena.juno.util.Delay;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A bounded, single-connection subset of the JDK {@code HttpServer}, dispatched cooperatively by {@link #handleNext}.
 * Context handlers and the exchange are allocated when the server is configured; request polling reuses its body
 * buffer and exchange object.
 */
public class HttpServer {
    static final int DEFAULT_BODY_CAPACITY = 512;

    static native void registerResponseStream(OutputStream stream);

    static native void beginResponse(int status, int contentType);

    static native void writeResponseByte(int value);

    /** The port the server listens on. */
    protected final int port;
    private final int bodyCapacity;
    private final HttpExchange exchange;
    private @Nullable HttpContext contexts = null;
    /** Whether {@link #start()} has run. */
    protected boolean started;

    /** A server for {@code port} whose request bodies are read into {@code bodyCapacity} bytes. */
    protected HttpServer(int port, int bodyCapacity) {
        this.port = port;
        this.bodyCapacity = bodyCapacity;
        exchange = new HttpExchange();
    }

    /** JDK-shaped listener factory; this board implementation binds the selected board's Wi-Fi interface. */
    public static HttpServer create(InetSocketAddress address, int backlog) {
        return new HttpServer(address.getPort(), DEFAULT_BODY_CAPACITY);
    }

    /** Registers a path-prefix handler before {@link #start()}. */
    public HttpContext createContext(String path, HttpHandler handler) {
        if (started) throw new IllegalStateException("server already started");
        HttpContext context = new HttpContext(path, handler, contexts);
        contexts = context;
        return context;
    }

    /** Starts the plain HTTP listener on the board's Wi-Fi interface. */
    public void start() {
        if (started) throw new IllegalStateException("server already started");
        io.github.jabrena.juno.hardware.net.http.HttpServer.begin(port);
        startDispatchLoop();
    }

    /** Starts the virtual thread that accepts requests and calls the matching handler. */
    protected final void startDispatchLoop() {
        if (started) throw new IllegalStateException("server already started");
        started = true;
        Thread.startVirtualThread(this::serve);
    }

    private void serve() {
        while (started) {
            try {
                handleNext();
                Delay.millis(10);
            } catch (IOException exception) {
                started = false;
            }
        }
    }

    /** Polls once and synchronously invokes the best matching handler, if a request is waiting. */
    private void handleNext() throws IOException {
        if (!started) throw new IllegalStateException("server not started");
        int length = io.github.jabrena.juno.hardware.net.http.HttpServer.accept(exchange.requestBuffer(), bodyCapacity);
        if (length < 0) return;
        exchange.begin(length);
        String path = io.github.jabrena.juno.hardware.net.http.HttpServer.path();
        HttpContext match = matchingContext(path);
        if (match == null) {
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(404, 9);
            OutputStream body = exchange.getResponseBody();
            String notFound = "not found";
            for (int index = 0; index < notFound.length(); index++) {
                body.write(notFound.charAt(index));
            }
            body.close();
        } else {
            match.handler().handle(exchange);
        }
    }

    private @Nullable HttpContext matchingContext(String path) {
        @Nullable HttpContext match = null;
        int longest = -1;
        for (HttpContext context = contexts; context != null; context = context.next()) {
            String prefix = context.path();
            if (hasPrefix(path, prefix) && prefix.length() > longest) {
                match = context;
                longest = prefix.length();
            }
        }
        return match;
    }

    private static boolean hasPrefix(String value, String prefix) {
        if (prefix.length() > value.length()) return false;
        for (int index = 0; index < prefix.length(); index++) {
            if (value.charAt(index) != prefix.charAt(index)) return false;
        }
        return true;
    }
}
