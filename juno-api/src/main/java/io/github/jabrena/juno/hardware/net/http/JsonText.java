package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/** {@link Json}'s JSON strings: validating one, and decoding its escapes to UTF-8 for {@link JsonValue}. */
final class JsonText {
    private JsonText() {
    }

    /** The index just past the JSON string starting at {@code start}, or {@link JsonSyntax#FAILED}. */
    static int parseString(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        if (start >= end || JsonChars.at(buffer, text, start) != '"') {
            return JsonSyntax.FAILED;
        }
        int p = start + 1;
        while (p < end) {
            int c = JsonChars.at(buffer, text, p++) & 0xFF;
            if (c == '"') {
                return p;
            }
            if (c < 0x20) {
                return JsonSyntax.FAILED;
            }
            if (c == '\\') {
                p = escapeEnd(buffer, text, p, end);
                if (p == JsonSyntax.FAILED) {
                    return JsonSyntax.FAILED;
                }
            }
        }
        return JsonSyntax.FAILED;
    }

    /**
     * Decodes the string content {@code [start, end)} to UTF-8 into {@code out}, or else appends each byte to
     * {@code builder} (a runtime {@code String} holds bytes), stopping before a character that would not fit
     * {@code capacity}, and returns the number of bytes written; {@code 0} for a lone or unpaired surrogate.
     */
    static int decode(byte @Nullable [] buffer, @Nullable String text, int start, int end, byte @Nullable [] out,
            @Nullable StringBuilder builder, int capacity) {
        int p = start;
        int written = 0;
        while (p < end) {
            int c = JsonChars.at(buffer, text, p++) & 0xFF;
            if (c != '\\' || JsonChars.at(buffer, text, p) != 'u') {
                if (written >= capacity) {
                    break;
                }
                written = put(out, builder, written, c == '\\' ? unescape(JsonChars.at(buffer, text, p++)) : c);
                continue;
            }
            int codePoint = hex4(buffer, text, p + 1);
            p += 5;
            if (codePoint >= 0xD800 && codePoint <= 0xDBFF) {
                int low = end - p < 6 || JsonChars.at(buffer, text, p) != '\\' || JsonChars.at(buffer, text, p + 1) != 'u' ? 0 : hex4(buffer, text, p + 2);
                if (low < 0xDC00 || low > 0xDFFF) {
                    return 0;
                }
                codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (low - 0xDC00);
                p += 6;
            } else if (codePoint >= 0xDC00 && codePoint <= 0xDFFF) {
                return 0;
            }
            if (written > capacity - utf8Length(codePoint)) {
                break;
            }
            written = writeUtf8(codePoint, out, builder, written);
        }
        return written;
    }

    private static int hex4(byte @Nullable [] buffer, @Nullable String text, int p) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value = value * 16 + hexValue(JsonChars.at(buffer, text, p + i));
        }
        return value;
    }

    private static int hexValue(int c) {
        if (JsonSyntax.isDigit(c)) {
            return c - '0';
        }
        return c >= 'a' && c <= 'f' ? c - 'a' + 10 : c - 'A' + 10;
    }

    private static int unescape(int escape) {
        if (escape == 'b') {
            return '\b';
        }
        if (escape == 'f') {
            return '\f';
        }
        if (escape == 'n') {
            return '\n';
        }
        if (escape == 'r') {
            return '\r';
        }
        return escape == 't' ? '\t' : escape;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        return codePoint <= 0xFFFF ? 3 : 4;
    }

    /** Writes {@code codePoint} as UTF-8 at position {@code written} and returns the new written count. */
    private static int writeUtf8(int codePoint, byte @Nullable [] out, @Nullable StringBuilder builder, int written) {
        int p = written;
        if (codePoint <= 0x7F) {
            p = put(out, builder, p, codePoint);
        } else if (codePoint <= 0x7FF) {
            p = put(out, builder, p, 0xC0 | (codePoint >> 6));
            p = put(out, builder, p, 0x80 | (codePoint & 0x3F));
        } else if (codePoint <= 0xFFFF) {
            p = put(out, builder, p, 0xE0 | (codePoint >> 12));
            p = put(out, builder, p, 0x80 | ((codePoint >> 6) & 0x3F));
            p = put(out, builder, p, 0x80 | (codePoint & 0x3F));
        } else {
            p = put(out, builder, p, 0xF0 | (codePoint >> 18));
            p = put(out, builder, p, 0x80 | ((codePoint >> 12) & 0x3F));
            p = put(out, builder, p, 0x80 | ((codePoint >> 6) & 0x3F));
            p = put(out, builder, p, 0x80 | (codePoint & 0x3F));
        }
        return p;
    }

    /** Writes one byte at {@code out[index]}, or appends it to {@code builder}, and returns {@code index + 1}. */
    private static int put(byte @Nullable [] out, @Nullable StringBuilder builder, int index, int value) {
        if (out != null) {
            out[index] = (byte) value;
        } else if (builder != null) {
            builder.append((char) (value & 0xFF));
        }
        return index + 1;
    }

    /** Past the escape whose backslash precedes {@code start}, or {@link JsonSyntax#FAILED} for an invalid one. */
    private static int escapeEnd(byte @Nullable [] buffer, @Nullable String text, int start, int end) {
        if (start >= end) {
            return JsonSyntax.FAILED;
        }
        int escape = JsonChars.at(buffer, text, start);
        if (escape == '"' || escape == '\\' || escape == '/' || escape == 'b' || escape == 'f' || escape == 'n'
                || escape == 'r' || escape == 't') {
            return start + 1;
        }
        int p = start + 1;
        if (escape != 'u' || end - p < 4) {
            return JsonSyntax.FAILED;
        }
        for (int i = 0; i < 4; i++) {
            if (!isHex(JsonChars.at(buffer, text, p + i))) {
                return JsonSyntax.FAILED;
            }
        }
        return p + 4;
    }

    private static boolean isHex(int c) {
        return JsonSyntax.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
