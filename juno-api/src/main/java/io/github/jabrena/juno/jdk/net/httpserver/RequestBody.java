package io.github.jabrena.juno.jdk.net.httpserver;

import java.io.InputStream;

/**
 * The current request's body, registered once with the board's HTTP transport: its reads drain the bytes
 * {@code accept()} received into the exchange's buffer. The JVM never runs it; the board lowers every read.
 */
final class RequestBody extends InputStream {
    RequestBody(byte[] buffer) {
        HttpServer.registerRequestStream(this, buffer);
    }

    @Override
    public int read() {
        return -1;
    }
}
