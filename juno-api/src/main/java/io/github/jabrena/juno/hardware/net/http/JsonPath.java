package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/** {@link Json}'s path lookup: validates the whole document, then walks a path's members and elements to a value. */
final class JsonPath {
    /** {@link #locate}: a step found no such member or element. */
    static final int MISSING = -1;
    /** {@link #locate}: the document or the path is malformed. */
    static final int INVALID = -2;

    private JsonPath() {
    }

    /** The index where the value at {@code path} starts, or {@link #MISSING} or {@link #INVALID}. */
    static int locate(byte @Nullable [] buffer, @Nullable String text, int length, String path) {
        if ((buffer == null && text == null) || path == null || length < 0) {
            return INVALID;
        }
        int rootStart = JsonSyntax.skipSpace(buffer, text, 0, length);
        int rootEnd = JsonSyntax.parseValue(buffer, text, rootStart, length, 0);
        if (rootEnd == JsonSyntax.FAILED || JsonSyntax.skipSpace(buffer, text, rootEnd, length) != length) {
            return INVALID;
        }
        int current = rootStart;
        int currentEnd = rootEnd;
        int cursor = 0;
        while (cursor < path.length()) {
            int next;
            if (path.charAt(cursor) == '[') {
                if (JsonChars.at(buffer, text, current) != '[') {
                    return MISSING;
                }
                int index = index(path, cursor + 1);
                if (index < 0) {
                    return INVALID;
                }
                next = digitsEnd(path, cursor + 1) + 1;
                current = element(buffer, text, current, currentEnd, index);
            } else {
                if (JsonChars.at(buffer, text, current) != '{') {
                    return MISSING;
                }
                next = nameEnd(path, cursor);
                if (next == cursor) {
                    return INVALID;
                }
                current = member(buffer, text, current, currentEnd, path, cursor, next);
            }
            if (current == JsonSyntax.FAILED) {
                return MISSING;
            }
            currentEnd = JsonSyntax.valueEnd(buffer, text, current, currentEnd);
            cursor = nextStep(path, next);
            if (cursor == INVALID) {
                return INVALID;
            }
        }
        return current;
    }

    /** Validates the whole document and returns where its root value starts, or {@link #INVALID}. */
    static int root(byte @Nullable [] buffer, @Nullable String text, int length) {
        if ((buffer == null && text == null) || length < 0) {
            return INVALID;
        }
        int rootStart = JsonSyntax.skipSpace(buffer, text, 0, length);
        int rootEnd = JsonSyntax.parseValue(buffer, text, rootStart, length, 0);
        if (rootEnd == JsonSyntax.FAILED || JsonSyntax.skipSpace(buffer, text, rootEnd, length) != length) {
            return INVALID;
        }
        return rootStart;
    }

    /**
     * The array index whose digits start at {@code start} and end with {@code ']'}, or {@code -1} for no digits, a
     * leading zero, an index past {@link Integer#MAX_VALUE}, or a missing {@code ']'}.
     */
    private static int index(String path, int start) {
        int end = digitsEnd(path, start);
        if (end == start || end >= path.length() || path.charAt(end) != ']') {
            return -1;
        }
        if (path.charAt(start) == '0' && end - start > 1) {
            return -1;
        }
        int index = 0;
        for (int i = start; i < end; i++) {
            int digit = path.charAt(i) - '0';
            if (index > (Integer.MAX_VALUE - digit) / 10) {
                return -1;
            }
            index = index * 10 + digit;
        }
        return index;
    }

    private static int digitsEnd(String path, int start) {
        int p = start;
        while (p < path.length() && JsonSyntax.isDigit(path.charAt(p))) {
            p++;
        }
        return p;
    }

    private static int nameEnd(String path, int start) {
        int p = start;
        while (p < path.length() && path.charAt(p) != '.' && path.charAt(p) != '[') {
            p++;
        }
        return p;
    }

    /** Where the step after the one ending at {@code end} starts: past a {@code '.'}, at a {@code '['}, or the end. */
    private static int nextStep(String path, int end) {
        if (end == path.length() || path.charAt(end) == '[') {
            return end;
        }
        if (path.charAt(end) != '.') {
            return INVALID;
        }
        int next = end + 1;
        if (next == path.length() || path.charAt(next) == '.' || path.charAt(next) == '[') {
            return INVALID;
        }
        return next;
    }

    static int member(byte @Nullable [] buffer, @Nullable String text, int objectStart, int objectEnd, String path, int keyStart, int keyEnd) {
        int p = JsonSyntax.skipSpace(buffer, text, objectStart + 1, objectEnd);
        while (p < objectEnd && JsonChars.at(buffer, text, p) != '}') {
            int nameEnd = JsonText.parseString(buffer, text, p, objectEnd);
            if (nameEnd == JsonSyntax.FAILED) {
                return JsonSyntax.FAILED;
            }
            boolean matches = nameMatches(buffer, text, p + 1, nameEnd - 1, path, keyStart, keyEnd);
            p = JsonSyntax.skipSpace(buffer, text, nameEnd, objectEnd);
            if (p >= objectEnd || JsonChars.at(buffer, text, p) != ':') {
                return JsonSyntax.FAILED;
            }
            int candidate = JsonSyntax.skipSpace(buffer, text, p + 1, objectEnd);
            p = JsonSyntax.parseValue(buffer, text, candidate, objectEnd, 1);
            if (p == JsonSyntax.FAILED || matches) {
                return p == JsonSyntax.FAILED ? JsonSyntax.FAILED : candidate;
            }
            p = JsonSyntax.skipComma(buffer, text, JsonSyntax.skipSpace(buffer, text, p, objectEnd), objectEnd);
        }
        return JsonSyntax.FAILED;
    }

    static int element(byte @Nullable [] buffer, @Nullable String text, int arrayStart, int arrayEnd, int index) {
        int p = JsonSyntax.skipSpace(buffer, text, arrayStart + 1, arrayEnd);
        int current = 0;
        while (p < arrayEnd && JsonChars.at(buffer, text, p) != ']') {
            int candidate = p;
            p = JsonSyntax.parseValue(buffer, text, p, arrayEnd, 1);
            if (p == JsonSyntax.FAILED || current == index) {
                return p == JsonSyntax.FAILED ? JsonSyntax.FAILED : candidate;
            }
            current++;
            p = JsonSyntax.skipComma(buffer, text, JsonSyntax.skipSpace(buffer, text, p, arrayEnd), arrayEnd);
        }
        return JsonSyntax.FAILED;
    }

    private static boolean nameMatches(byte @Nullable [] buffer, @Nullable String text, int nameStart, int nameEnd, String path, int keyStart,
            int keyEnd) {
        if (nameEnd - nameStart != keyEnd - keyStart) {
            return false;
        }
        for (int i = 0; i < nameEnd - nameStart; i++) {
            if ((JsonChars.at(buffer, text, nameStart + i) & 0xFF) != path.charAt(keyStart + i)) {
                return false;
            }
        }
        return true;
    }
}
