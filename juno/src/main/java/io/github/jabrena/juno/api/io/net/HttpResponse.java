package io.github.jabrena.juno.api.io.net;

/**
 * An immutable snapshot of one {@link HttpClient}/{@link HttpsClient} response: {@code status},
 * {@code body}/{@code headers} (the caller-owned buffers passed into the request call), and
 * {@code bodyLength}/{@code headersLength} (the number of bytes actually written into each,
 * truncated to the buffer's own length if the response was longer). {@code status} is the HTTP
 * status code (e.g. {@code 200}), or {@code 0} if the connection failed before a status line was
 * received.
 *
 * <p>A fresh instance is constructed on every request — cheap, since it only holds three
 * {@code int}s and two array references, never a copy of the buffers themselves. Juno's arena
 * never frees memory, so a caller looping forever (like {@code MadridWeather}) should still keep
 * {@code body}/{@code headers} themselves allocated once outside the loop and pass the same
 * buffers into every call; only this small wrapper is allocated per call. {@code juno inspect
 * --risks} estimates how many iterations that budget allows.
 */
public final class HttpResponse {
    public final int status;
    public final int bodyLength;
    public final int headersLength;
    public final byte[] body;
    public final byte[] headers;

    public HttpResponse(int status, int bodyLength, int headersLength, byte[] body, byte[] headers) {
        this.status = status;
        this.bodyLength = bodyLength;
        this.headersLength = headersLength;
        this.body = body;
        this.headers = headers;
    }
}
