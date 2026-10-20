package io.github.jabrena.juno.hardware.net.http;

/** {@link Json}'s path lookup: validates the whole document, then walks a path's members and elements to a value. */
final class JsonPath {
    /** {@link #locate}: a step found no such member or element. */
    static final int MISSING = -1;
    /** {@link #locate}: the document or the path is malformed. */
    static final int INVALID = -2;

    private JsonPath() {
    }

    /** The index where the value at {@code path} starts, or {@link #MISSING} or {@link #INVALID}. */
    static int locate(byte[] buffer, int length, String path) {
        if (buffer == null || path == null || length < 0) {
            return INVALID;
        }
        int rootStart = JsonSyntax.skipSpace(buffer, 0, length);
        int rootEnd = JsonSyntax.parseValue(buffer, rootStart, length, 0);
        if (rootEnd == JsonSyntax.FAILED || JsonSyntax.skipSpace(buffer, rootEnd, length) != length) {
            return INVALID;
        }
        int current = rootStart;
        int currentEnd = rootEnd;
        int cursor = 0;
        while (cursor < path.length()) {
            int next;
            if (path.charAt(cursor) == '[') {
                if (buffer[current] != '[') {
                    return MISSING;
                }
                int index = index(path, cursor + 1);
                if (index < 0) {
                    return INVALID;
                }
                next = digitsEnd(path, cursor + 1) + 1;
                current = element(buffer, current, currentEnd, index);
            } else {
                if (buffer[current] != '{') {
                    return MISSING;
                }
                next = nameEnd(path, cursor);
                if (next == cursor) {
                    return INVALID;
                }
                current = member(buffer, current, currentEnd, path, cursor, next);
            }
            if (current == JsonSyntax.FAILED) {
                return MISSING;
            }
            currentEnd = JsonSyntax.valueEnd(buffer, current, currentEnd);
            cursor = nextStep(path, next);
            if (cursor == INVALID) {
                return INVALID;
            }
        }
        return current;
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

    private static int member(byte[] buffer, int objectStart, int objectEnd, String path, int keyStart, int keyEnd) {
        int p = JsonSyntax.skipSpace(buffer, objectStart + 1, objectEnd);
        while (p < objectEnd && buffer[p] != '}') {
            int nameEnd = JsonText.parseString(buffer, p, objectEnd);
            if (nameEnd == JsonSyntax.FAILED) {
                return JsonSyntax.FAILED;
            }
            boolean matches = nameMatches(buffer, p + 1, nameEnd - 1, path, keyStart, keyEnd);
            p = JsonSyntax.skipSpace(buffer, nameEnd, objectEnd);
            if (p >= objectEnd || buffer[p] != ':') {
                return JsonSyntax.FAILED;
            }
            int candidate = JsonSyntax.skipSpace(buffer, p + 1, objectEnd);
            p = JsonSyntax.parseValue(buffer, candidate, objectEnd, 1);
            if (p == JsonSyntax.FAILED || matches) {
                return p == JsonSyntax.FAILED ? JsonSyntax.FAILED : candidate;
            }
            p = JsonSyntax.skipComma(buffer, JsonSyntax.skipSpace(buffer, p, objectEnd), objectEnd);
        }
        return JsonSyntax.FAILED;
    }

    private static int element(byte[] buffer, int arrayStart, int arrayEnd, int index) {
        int p = JsonSyntax.skipSpace(buffer, arrayStart + 1, arrayEnd);
        int current = 0;
        while (p < arrayEnd && buffer[p] != ']') {
            int candidate = p;
            p = JsonSyntax.parseValue(buffer, p, arrayEnd, 1);
            if (p == JsonSyntax.FAILED || current == index) {
                return p == JsonSyntax.FAILED ? JsonSyntax.FAILED : candidate;
            }
            current++;
            p = JsonSyntax.skipComma(buffer, JsonSyntax.skipSpace(buffer, p, arrayEnd), arrayEnd);
        }
        return JsonSyntax.FAILED;
    }

    private static boolean nameMatches(byte[] buffer, int nameStart, int nameEnd, String path, int keyStart,
            int keyEnd) {
        if (nameEnd - nameStart != keyEnd - keyStart) {
            return false;
        }
        for (int i = 0; i < nameEnd - nameStart; i++) {
            if ((buffer[nameStart + i] & 0xFF) != path.charAt(keyStart + i)) {
                return false;
            }
        }
        return true;
    }
}
