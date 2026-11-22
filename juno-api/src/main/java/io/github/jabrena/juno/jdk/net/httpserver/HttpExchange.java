package io.github.jabrena.juno.jdk.net.httpserver;

import io.github.jabrena.juno.jdk.net.URI;

import java.io.IOException;
import java.io.OutputStream;

/** The bounded request view for the request currently dispatched by {@link HttpServer}. */
public final class HttpExchange {
    private static final int REQUEST_BODY_CAPACITY = 512;
    private final byte[] requestBody = new byte[REQUEST_BODY_CAPACITY];
    private final Headers responseHeaders = new Headers();
    private final ResponseBody responseBody = new ResponseBody();
    private int requestBodyLength;

    HttpExchange() {
    }

    void begin(int bodyLength) {
        requestBodyLength = bodyLength;
    }

    byte[] requestBuffer() {
        return requestBody;
    }

    /** The request method, valid only while the handler is running. */
    public String getRequestMethod() {
        return io.github.jabrena.juno.hardware.net.http.HttpServer.method();
    }

    /** The bounded request target/path, valid only while the handler is running. */
    public URI getRequestURI() {
        return URI.create(io.github.jabrena.juno.hardware.net.http.HttpServer.path());
    }

    public Headers getResponseHeaders() {
        return responseHeaders;
    }

    public void sendResponseHeaders(int status, long responseLength) throws IOException {
        HttpServer.beginResponse(status, responseHeaders.contentType());
    }

    public OutputStream getResponseBody() {
        return responseBody;
    }

}
