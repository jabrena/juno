package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

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
    static int valueEnd(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        return parseValue(buffer, text, start, end, 0);
    }

    /** The index just past the value starting at or after {@code start}, or {@link #FAILED}. */
    static int parseValue(byte @Nullable [] buffer, @Nullable String text, int start, int end, int depth) {
        if (depth > MAX_DEPTH) {
            return FAILED;
        }
        int p = skipSpace(buffer, text, start, end);
        if (p >= end) {
            return FAILED;
        }
        int first = JsonChars.at(buffer, text, p);
        if (first == '"') {
            return JsonText.parseString(buffer, text, p, end);
        }
        if (first == '{') {
            return parseObject(buffer, text, p, end, depth);
        }
        if (first == '[') {
            return parseArray(buffer, text, p, end, depth);
        }
        if (isWord(buffer, text, p, end, 't', 'r', 'u', 'e') || isWord(buffer, text, p, end, 'n', 'u', 'l', 'l')) {
            return p + 4;
        }
        if (end - p >= 5 && isWord(buffer, text, p, end, 'f', 'a', 'l', 's') && JsonChars.at(buffer, text, p + 4) == 'e') {
            return p + 5;
        }
        return JsonNumbers.parseNumber(buffer, text, p, end);
    }

    static int skipSpace(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        int p = start;
        while (p < end && (JsonChars.at(buffer, text, p) == ' ' || JsonChars.at(buffer, text, p) == '\t' || JsonChars.at(buffer, text, p) == '\r' || JsonChars.at(buffer, text, p) == '\n')) {
            p++;
        }
        return p;
    }

    /** Past a member or element separator and the space after it, or {@code start} when there is none. */
    static int skipComma(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        return start < end && JsonChars.at(buffer, text, start) == ',' ? skipSpace(buffer, text, start + 1, end) : start;
    }

    static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static int parseObject(byte @Nullable [] buffer, @Nullable String text, int start, int end, int depth) {
        int p = skipSpace(buffer, text, start + 1, end);
        if (p < end && JsonChars.at(buffer, text, p) == '}') {
            return p + 1;
        }
        while (p != FAILED && p < end) {
            p = JsonText.parseString(buffer, text, p, end);
            if (p == FAILED) {
                return FAILED;
            }
            p = skipSpace(buffer, text, p, end);
            if (p >= end || JsonChars.at(buffer, text, p) != ':') {
                return FAILED;
            }
            p = parseValue(buffer, text, skipSpace(buffer, text, p + 1, end), end, depth + 1);
            if (p == FAILED) {
                return FAILED;
            }
            p = skipSpace(buffer, text, p, end);
            if (p < end && JsonChars.at(buffer, text, p) == '}') {
                return p + 1;
            }
            p = nextItem(buffer, text, p, end, '}');
        }
        return FAILED;
    }

    private static int parseArray(byte @Nullable [] buffer, @Nullable String text, int start, int end, int depth) {
        int p = skipSpace(buffer, text, start + 1, end);
        if (p < end && JsonChars.at(buffer, text, p) == ']') {
            return p + 1;
        }
        while (p != FAILED && p < end) {
            p = parseValue(buffer, text, p, end, depth + 1);
            if (p == FAILED) {
                return FAILED;
            }
            p = skipSpace(buffer, text, p, end);
            if (p < end && JsonChars.at(buffer, text, p) == ']') {
                return p + 1;
            }
            p = nextItem(buffer, text, p, end, ']');
        }
        return FAILED;
    }

    /**
     * Past the {@code ','} at {@code start} and the space after it, or {@link #FAILED} when there is no comma or a
     * {@code close} follows it (a trailing comma).
     */
    private static int nextItem(byte @Nullable [] buffer, @Nullable String text, int start, int end, char close) {
        if (start >= end || JsonChars.at(buffer, text, start) != ',') {
            return FAILED;
        }
        int p = skipSpace(buffer, text, start + 1, end);
        return p < end && JsonChars.at(buffer, text, p) == close ? FAILED : p;
    }

    private static boolean isWord(byte @Nullable [] buffer, @Nullable String text, int p, int end, char a, char b, char c, char d) {
        return end - p >= 4 && JsonChars.at(buffer, text, p) == a && JsonChars.at(buffer, text, p + 1) == b && JsonChars.at(buffer, text, p + 2) == c && JsonChars.at(buffer, text, p + 3) == d;
    }
}
