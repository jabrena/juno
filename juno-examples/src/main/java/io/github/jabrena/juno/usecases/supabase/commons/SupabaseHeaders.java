package io.github.jabrena.juno.usecases.supabase.commons;

/** Builds the example client's CRLF-delimited request-header block directly in caller-owned storage. */
final class SupabaseHeaders {
    private SupabaseHeaders() {
    }

    static int standard(byte[] buffer, int capacity, String publishableKey) {
        int length = append(buffer, capacity, 0, "apikey: ", publishableKey);
        return append(buffer, capacity, length, "Prefer: ", "return=representation");
    }

    static int appendAuthorization(byte[] buffer, int capacity, int length, String accessToken) {
        return append(buffer, capacity, length, "Authorization: Bearer ", accessToken);
    }

    static int appendExactCount(byte[] buffer, int capacity, int length) {
        return append(buffer, capacity, length, "Prefer: ", "count=exact");
    }

    private static int append(byte[] buffer, int capacity, int offset, String name, String value) {
        int next = appendAscii(buffer, capacity, offset, name);
        next = appendAscii(buffer, capacity, next, value);
        if (next < 0 || next + 2 > capacity) {
            return -1;
        }
        buffer[next] = '\r';
        buffer[next + 1] = '\n';
        return next + 2;
    }

    private static int appendAscii(byte[] buffer, int capacity, int offset, String value) {
        if (offset < 0) {
            return -1;
        }
        int length = value.length();
        int index = 0;
        while (index < length) {
            int character = value.charAt(index);
            if (character > 127 || offset >= capacity) {
                return -1;
            }
            buffer[offset] = (byte) character;
            offset = offset + 1;
            index = index + 1;
        }
        return offset;
    }
}
