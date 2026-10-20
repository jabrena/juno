package io.github.jabrena.juno.hardware.net.http;

/**
 * {@link Json}'s validating scanner: each method takes the index where a piece of JSON starts and returns the index
 * just past it, or {@link #FAILED}, so a scan needs no out-parameters and allocates nothing.
 */
final class JsonSyntax {
    /** A parse failure, as an index no scan ever reaches. */
    static final int FAILED = -1;

    private static final int MAX_DEPTH = 32;

    private JsonSyntax() {
    }

    /** Where the already-validated value starting at {@code start} ends. */
    static int valueEnd(byte[] buffer, int start, int end) {
        return parseValue(buffer, start, end, 0);
    }

    /** The index just past the value starting at or after {@code start}, or {@link #FAILED}. */
    static int parseValue(byte[] buffer, int start, int end, int depth) {
        if (depth > MAX_DEPTH) {
            return FAILED;
        }
        int p = skipSpace(buffer, start, end);
        if (p >= end) {
            return FAILED;
        }
        int first = buffer[p];
        if (first == '"') {
            return JsonText.parseString(buffer, p, end);
        }
        if (first == '{') {
            return parseObject(buffer, p, end, depth);
        }
        if (first == '[') {
            return parseArray(buffer, p, end, depth);
        }
        if (isWord(buffer, p, end, 't', 'r', 'u', 'e') || isWord(buffer, p, end, 'n', 'u', 'l', 'l')) {
            return p + 4;
        }
        if (end - p >= 5 && isWord(buffer, p, end, 'f', 'a', 'l', 's') && buffer[p + 4] == 'e') {
            return p + 5;
        }
        return JsonNumbers.parseNumber(buffer, p, end);
    }

    static int skipSpace(byte[] buffer, int start, int end) {
        int p = start;
        while (p < end && (buffer[p] == ' ' || buffer[p] == '\t' || buffer[p] == '\r' || buffer[p] == '\n')) {
            p++;
        }
        return p;
    }

    /** Past a member or element separator and the space after it, or {@code start} when there is none. */
    static int skipComma(byte[] buffer, int start, int end) {
        return start < end && buffer[start] == ',' ? skipSpace(buffer, start + 1, end) : start;
    }

    static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static int parseObject(byte[] buffer, int start, int end, int depth) {
        int p = skipSpace(buffer, start + 1, end);
        if (p < end && buffer[p] == '}') {
            return p + 1;
        }
        while (p != FAILED && p < end) {
            p = JsonText.parseString(buffer, p, end);
            if (p == FAILED) {
                return FAILED;
            }
            p = skipSpace(buffer, p, end);
            if (p >= end || buffer[p] != ':') {
                return FAILED;
            }
            p = parseValue(buffer, skipSpace(buffer, p + 1, end), end, depth + 1);
            if (p == FAILED) {
                return FAILED;
            }
            p = skipSpace(buffer, p, end);
            if (p < end && buffer[p] == '}') {
                return p + 1;
            }
            p = nextItem(buffer, p, end, '}');
        }
        return FAILED;
    }

    private static int parseArray(byte[] buffer, int start, int end, int depth) {
        int p = skipSpace(buffer, start + 1, end);
        if (p < end && buffer[p] == ']') {
            return p + 1;
        }
        while (p != FAILED && p < end) {
            p = parseValue(buffer, p, end, depth + 1);
            if (p == FAILED) {
                return FAILED;
            }
            p = skipSpace(buffer, p, end);
            if (p < end && buffer[p] == ']') {
                return p + 1;
            }
            p = nextItem(buffer, p, end, ']');
        }
        return FAILED;
    }

    /**
     * Past the {@code ','} at {@code start} and the space after it, or {@link #FAILED} when there is no comma or a
     * {@code close} follows it (a trailing comma).
     */
    private static int nextItem(byte[] buffer, int start, int end, char close) {
        if (start >= end || buffer[start] != ',') {
            return FAILED;
        }
        int p = skipSpace(buffer, start + 1, end);
        return p < end && buffer[p] == close ? FAILED : p;
    }

    private static boolean isWord(byte[] buffer, int p, int end, char a, char b, char c, char d) {
        return end - p >= 4 && buffer[p] == a && buffer[p + 1] == b && buffer[p + 2] == c && buffer[p + 3] == d;
    }
}
