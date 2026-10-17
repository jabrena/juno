package io.github.jabrena.juno.usecases.supabase.commons;

/**
 * Reusable result storage for {@link SupabaseClient}. The body, response-header, and two-integer status buffers are
 * supplied by the caller and retained; executing another request overwrites their contents. Construct this once at
 * startup and reuse it. No request allocates.
 */
public final class SupabaseResponse {
    private final byte[] bodyBuffer;
    private final int bodyCapacity;
    private final byte[] headersBuffer;
    private final int headersCapacity;
    private final int[] statusAndHeadersLength;
    private int bodyLength = -1;

    /** Creates reusable response storage over the caller-owned buffers. */
    public SupabaseResponse(
            byte[] bodyBuffer,
            int bodyCapacity,
            byte[] headersBuffer,
            int headersCapacity,
            int[] statusAndHeadersLength) {
        this.bodyBuffer = bodyBuffer;
        this.bodyCapacity = bodyCapacity;
        this.headersBuffer = headersBuffer;
        this.headersCapacity = headersCapacity;
        this.statusAndHeadersLength = statusAndHeadersLength;
    }

    /** The caller-owned response-body buffer. Only the first {@link #bodyLength()} bytes belong to this response. */
    public byte[] bodyBuffer() {
        return bodyBuffer;
    }

    /** The number of response-body bytes captured, or {@code -1} when the request was not sent or transport failed. */
    public int bodyLength() {
        return bodyLength;
    }

    /** The HTTP status code, or {@code 0} when no status line arrived. */
    public int statusCode() {
        return statusAndHeadersLength[0];
    }

    /** The caller-owned raw response-header buffer. */
    public byte[] headersBuffer() {
        return headersBuffer;
    }

    /** The number of raw response-header bytes captured. */
    public int headersLength() {
        return statusAndHeadersLength[1];
    }

    /** Whether transport succeeded and Supabase returned a 2xx status. */
    public boolean successful() {
        return bodyLength >= 0 && statusCode() >= 200 && statusCode() < 300;
    }

    int bodyCapacity() {
        return bodyCapacity;
    }

    int headersCapacity() {
        return headersCapacity;
    }

    int[] statusAndHeadersLength() {
        return statusAndHeadersLength;
    }

    void completed(int length) {
        bodyLength = length;
    }

    void rejected() {
        statusAndHeadersLength[0] = 0;
        statusAndHeadersLength[1] = 0;
        bodyLength = -1;
    }
}
