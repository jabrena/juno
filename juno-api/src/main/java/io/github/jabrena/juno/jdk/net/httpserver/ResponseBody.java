package io.github.jabrena.juno.jdk.net.httpserver;

import java.io.IOException;
import java.io.OutputStream;

/** Fixed-buffer response stream registered with the board's HTTP transport. */
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
