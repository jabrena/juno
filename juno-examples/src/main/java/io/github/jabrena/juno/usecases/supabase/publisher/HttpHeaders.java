package io.github.jabrena.juno.usecases.supabase.publisher;

/**
 * Caller-chosen request headers, added as name/value pairs such as {@code add("apikey", key)}, written into a
 * caller-owned buffer as CRLF-terminated lines ({@code name: value\r\n}), the form {@code HttpClient.post}'s
 * request-header buffer takes. Names and values must be ASCII. The instance is created once at startup and nothing
 * is allocated afterwards.
 */
final class HttpHeaders {
    private final byte[] buffer;
    private final int capacity;
    private int length;

    HttpHeaders(byte[] buffer, int capacity) {
        this.buffer = buffer;
        this.capacity = capacity;
    }

    /** Appends {@code name: value}; a header that does not fit, or is not ASCII, makes {@link #length()} -1. */
    HttpHeaders add(String name, String value) {
        appendAscii(name);
        appendAscii(": ");
        appendAscii(value);
        appendAscii("\r\n");
        return this;
    }

    /** The buffer the headers are written to. */
    byte[] buffer() {
        return buffer;
    }

    /** The written length, or {@code -1} after a header did not fit or was not ASCII. */
    int length() {
        return length;
    }

    private void appendAscii(String value) {
        int count = value.length();
        int index = 0;
        while (index < count && length >= 0) {
            int character = value.charAt(index);
            if (character > 127 || length >= capacity) {
                length = -1;
            } else {
                buffer[length] = (byte) character;
                length = length + 1;
            }
            index = index + 1;
        }
    }
}
