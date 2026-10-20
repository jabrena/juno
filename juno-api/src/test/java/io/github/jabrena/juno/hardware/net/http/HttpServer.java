package io.github.jabrena.juno.hardware.net.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Test-only stand-in for the HTTP server intrinsics. Each {@link #accept} takes the next scripted {@link Request}; a
 * request with a null body is an idle poll that accept reports as nothing received. Responses are recorded in
 * {@link #responses}. When the script runs out, accept throws, which ends a test that would otherwise loop forever.
 */
public final class HttpServer {
    public static final int DEFAULT_BODY_BUFFER_SIZE = 512;

    /** One scripted request: its method, path and body, or a null body for an idle poll. */
    public record Request(String method, String path, String body) {
    }

    /** One recorded response. */
    public record Response(int status, String contentType, String body) {
    }

    public static final Deque<Request> requests = new ArrayDeque<>();
    public static final List<Response> responses = new ArrayList<>();
    public static int port;
    private static Request current;

    private HttpServer() {
    }

    public static void reset() {
        requests.clear();
        responses.clear();
        port = 0;
        current = null;
    }

    public static void begin(int port) {
        HttpServer.port = port;
    }

    public static int accept(byte[] bodyBuffer, int bodyBufferLength) {
        if (requests.isEmpty()) {
            throw new IllegalStateException("the scripted requests ran out");
        }
        current = requests.poll();
        if (current.body() == null) {
            return -1;
        }
        byte[] body = current.body().getBytes(StandardCharsets.US_ASCII);
        int length = Math.min(body.length, bodyBufferLength);
        System.arraycopy(body, 0, bodyBuffer, 0, length);
        return length;
    }

    public static String method() {
        return current.method();
    }

    public static String path() {
        return current.path();
    }

    public static void respond(int status, String contentType, String body) {
        responses.add(new Response(status, contentType, body));
    }

    public static void respond(int status, String contentType, StringBuilder body) {
        responses.add(new Response(status, contentType, body.toString()));
    }
}
