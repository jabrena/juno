package io.github.jabrena.juno.jdk.net.httpserver;

import java.io.IOException;
import java.io.OutputStream;

/** The response body, registered with the board's HTTP transport, which sends its bytes as they are written. */
final class ResponseBody extends OutputStream {
    ResponseBody() {
        HttpServer.registerResponseStream(this);
    }

    @Override
    public void write(int value) throws IOException {
        HttpServer.writeResponseByte(value);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        for (int index = 0; index < length; index++) {
            write(bytes[offset + index]);
        }
    }
}
