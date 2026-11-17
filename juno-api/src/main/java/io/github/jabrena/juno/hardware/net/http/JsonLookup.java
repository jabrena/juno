package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/** {@link JsonView}'s lookups: validates a whole document, and finds a member or an element of a validated value. */
final class JsonLookup {
    /** No value exists at a step. */
    static final int MISSING = -1;
    /** The document is malformed. */
    static final int INVALID = -2;

    private JsonLookup() {
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

    /** Where the value of member {@code name} of the object {@code [objectStart, objectEnd)} starts, or {@link JsonSyntax#FAILED}. */
    static int member(byte @Nullable [] buffer, @Nullable String text, int objectStart, int objectEnd, String name) {
        int p = JsonSyntax.skipSpace(buffer, text, objectStart + 1, objectEnd);
        while (p < objectEnd && JsonChars.at(buffer, text, p) != '}') {
            int nameEnd = JsonText.parseString(buffer, text, p, objectEnd);
            if (nameEnd == JsonSyntax.FAILED) {
                return JsonSyntax.FAILED;
            }
            boolean matches = nameMatches(buffer, text, p + 1, nameEnd - 1, name);
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

    /** Where element {@code index} of the array {@code [arrayStart, arrayEnd)} starts, or {@link JsonSyntax#FAILED}. */
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

    private static boolean nameMatches(byte @Nullable [] buffer, @Nullable String text, int nameStart, int nameEnd, String name) {
        if (nameEnd - nameStart != name.length()) {
            return false;
        }
        for (int i = 0; i < nameEnd - nameStart; i++) {
            if ((JsonChars.at(buffer, text, nameStart + i) & 0xFF) != name.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
