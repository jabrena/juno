package io.github.jabrena.juno.usecases.supabase.commons;

/** Reads the exact total from the {@code Content-Range} header returned for a counted PostgREST select. */
public final class SupabaseContentRange {
    private static final int UNKNOWN = -1;
    private static final String CONTENT_RANGE = "content-range:";

    private SupabaseContentRange() {
    }

    /** Returns the total after the {@code /} in {@code Content-Range}, or {@value #UNKNOWN} when absent or unknown. */
    public static int total(SupabaseResponse response) {
        byte[] headers = response.headersBuffer();
        int length = response.headersLength();
        int line = 0;
        while (line < length) {
            int lineEnd = line;
            while (lineEnd < length && headers[lineEnd] != '\n') {
                lineEnd = lineEnd + 1;
            }
            if (startsWithContentRange(headers, line, lineEnd)) {
                return totalAfterSlash(headers, line, lineEnd);
            }
            line = lineEnd + 1;
        }
        return UNKNOWN;
    }

    private static boolean startsWithContentRange(byte[] headers, int line, int lineEnd) {
        if (lineEnd - line < CONTENT_RANGE.length()) {
            return false;
        }
        int index = 0;
        while (index < CONTENT_RANGE.length()) {
            int value = headers[line + index];
            if (value >= 'A' && value <= 'Z') {
                value = value + ('a' - 'A');
            }
            if (value != CONTENT_RANGE.charAt(index)) {
                return false;
            }
            index = index + 1;
        }
        return true;
    }

    private static int totalAfterSlash(byte[] headers, int line, int lineEnd) {
        int index = line;
        while (index < lineEnd && headers[index] != '/') {
            index = index + 1;
        }
        index = index + 1;
        if (index >= lineEnd || headers[index] < '0' || headers[index] > '9') {
            return UNKNOWN;
        }
        int result = 0;
        while (index < lineEnd && headers[index] >= '0' && headers[index] <= '9') {
            result = result * 10 + headers[index] - '0';
            index = index + 1;
        }
        return result;
    }
}
