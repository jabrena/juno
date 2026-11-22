package io.github.jabrena.juno.jdk.net.httpserver;

import io.github.jabrena.juno.hardware.internal.NativeHttpServer;
import io.github.jabrena.juno.jdk.net.URI;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** The bounded request view for the request currently dispatched by {@link HttpServer}. */
public final class HttpExchange {
    private final byte[] requestBuffer;
    private final Headers responseHeaders = Headers.response();
    /** Made on first use, so a server that never reads them spends no memory on request headers or the body stream. */
    private @Nullable Headers requestHeaders;
    private @Nullable RequestBody requestBody;
    private final ResponseBody responseBody = new ResponseBody();

    HttpExchange(byte[] requestBuffer) {
        this.requestBuffer = requestBuffer;
    }

    byte[] requestBuffer() {
        return requestBuffer;
    }

    /** The request method, valid only while the handler is running. */
    public String getRequestMethod() {
        return NativeHttpServer.method();
    }

    /** The bounded request target/path, valid only while the handler is running. */
    public URI getRequestURI() {
        return URI.create(NativeHttpServer.path());
    }

    /** The request headers, read by name with {@link Headers#getFirst(String)}. */
    public Headers getRequestHeaders() {
        Headers headers = requestHeaders;
        if (headers == null) {
            headers = Headers.request();
            requestHeaders = headers;
        }
        return headers;
    }

    /**
     * The request body. Its first 512 bytes arrive with the request; reading past them takes the rest from the client,
     * waiting up to five seconds for it. {@code available()} tells how many declared bytes are left to read.
     */
    public InputStream getRequestBody() {
        RequestBody body = requestBody;
        if (body == null) {
            body = new RequestBody(requestBuffer);
            requestBody = body;
        }
        return body;
    }

    /** The response headers, to set before {@link #sendResponseHeaders(int, long)}. */
    public Headers getResponseHeaders() {
        return responseHeaders;
    }

    /**
     * Sends the status line and headers. A {@code responseLength} above zero is sent as {@code Content-Length}; zero
     * sends a body of any length that ends when the connection closes, and {@code -1} means no body.
     */
    public void sendResponseHeaders(int status, long responseLength) throws IOException {
        int length = responseLength > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) responseLength;
        HttpServer.beginResponse(status, responseHeaders.contentType(), length);
    }

    /** The response body; its bytes go to the client as they are written, and closing it ends the response. */
    public OutputStream getResponseBody() {
        return responseBody;
    }

}
