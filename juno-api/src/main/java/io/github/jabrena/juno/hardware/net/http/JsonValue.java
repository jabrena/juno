package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/**
 * One value inside a JSON document, with the navigation and conversion methods of JDK 28's {@code JsonValue}
 * (JEP 540): {@code get(String)}, {@code get(int)}, {@code asInt()}, {@code asString()}, {@code toString()}. A value is
 * the document plus the index where it starts: it never copies the document or parses it into a tree.
 * {@link Json#parse(String)} validates the whole document once, so each later step only scans the value it starts
 * from.
 *
 * <pre>{@code
 * JsonValue puzzle = Json.parse(response.body()).get("puzzle");
 * int rating = puzzle.get("rating").asInt();
 * JsonValue solution = puzzle.get("solution");
 * for (int i = 0; i < solution.size(); i++) {
 *     int length = solution.get(i).copyString(uci);
 * }
 * }</pre>
 *
 * <p>Unlike JEP 540 nothing throws: a missing member, an index out of range, a step into a non-container or an invalid
 * document gives a value whose {@link #type()} says so, every later step keeps that answer, and the {@code as*}
 * conversions return zero values or {@code null}. As in JEP 540, {@code asInt()} and {@code asLong()} accept any
 * integral number ({@code 123}, {@code 123.0}, {@code 2.3E2}) and reject a fraction or an overflow.
 *
 * <p>Each {@code get} returns a new value, one 20-byte arena object that the collector reclaims; keep a value in a local
 * variable rather than repeating a step. A value reads the document where it is, so a {@code byte[]} buffer must not
 * be reused for another document while its values are still read.
 */
public final class JsonValue {
    private final byte @Nullable [] buffer;
    private final @Nullable String text;
    private final int length;
    /** Where the value starts, or {@link JsonLookup#MISSING} or {@link JsonLookup#INVALID}. */
    private final int start;

    JsonValue(byte @Nullable [] buffer, @Nullable String text, int length, int start) {
        this.buffer = buffer;
        this.text = text;
        this.length = length;
        this.start = start;
    }

    /** The member {@code name} of this object; a missing value when absent or when this is not an object. */
    public JsonValue get(String name) {
        if (start < 0) {
            return this;
        }
        if (JsonChars.at(buffer, text, start) != '{') {
            return at(JsonLookup.MISSING);
        }
        int member = JsonLookup.member(buffer, text, start, length, name);
        return at(member == JsonSyntax.FAILED ? JsonLookup.MISSING : member);
    }

    /** The element {@code index} of this array; a missing value when out of range or when this is not an array. */
    public JsonValue get(int index) {
        if (start < 0) {
            return this;
        }
        if (JsonChars.at(buffer, text, start) != '[' || index < 0) {
            return at(JsonLookup.MISSING);
        }
        int element = JsonLookup.element(buffer, text, start, length, index);
        return at(element == JsonSyntax.FAILED ? JsonLookup.MISSING : element);
    }

    /** One of {@link Json}'s {@code TYPE_*} constants. */
    public int type() {
        if (start == JsonLookup.INVALID) {
            return Json.TYPE_INVALID;
        }
        if (start == JsonLookup.MISSING) {
            return Json.TYPE_MISSING;
        }
        int first = JsonChars.at(buffer, text, start);
        if (first == 'n') {
            return Json.TYPE_NULL;
        }
        if (first == 't' || first == 'f') {
            return Json.TYPE_BOOLEAN;
        }
        if (first == '"') {
            return Json.TYPE_STRING;
        }
        if (first == '{') {
            return Json.TYPE_OBJECT;
        }
        return first == '[' ? Json.TYPE_ARRAY : Json.TYPE_NUMBER;
    }

    /** Whether no value exists here: an absent member, an index out of range, or a step into a non-container. */
    public boolean isMissing() {
        return start == JsonLookup.MISSING;
    }

    /** Whether the document this value came from is malformed or truncated. */
    public boolean isInvalid() {
        return start == JsonLookup.INVALID;
    }

    /** Whether this is the JSON literal {@code null}. */
    public boolean isNull() {
        return type() == Json.TYPE_NULL;
    }

    /** Whether this is {@code true} or {@code false}. */
    public boolean isBoolean() {
        return type() == Json.TYPE_BOOLEAN;
    }

    /** Whether this is a JSON number. */
    public boolean isNumber() {
        return type() == Json.TYPE_NUMBER;
    }

    /** Whether this is a JSON string. */
    public boolean isString() {
        return type() == Json.TYPE_STRING;
    }

    /** Whether this is a JSON object. */
    public boolean isObject() {
        return type() == Json.TYPE_OBJECT;
    }

    /** Whether this is a JSON array. */
    public boolean isArray() {
        return type() == Json.TYPE_ARRAY;
    }

    /** The number of members of an object or elements of an array, or {@code -1} for any other value. */
    public int size() {
        int type = type();
        if (type != Json.TYPE_OBJECT && type != Json.TYPE_ARRAY) {
            return -1;
        }
        int close = type == Json.TYPE_OBJECT ? '}' : ']';
        int p = JsonSyntax.skipSpace(buffer, text, start + 1, length);
        int size = 0;
        while (p < length && JsonChars.at(buffer, text, p) != close) {
            if (type == Json.TYPE_OBJECT) {
                p = JsonSyntax.skipSpace(buffer, text, JsonText.parseString(buffer, text, p, length), length) + 1;
            }
            p = JsonSyntax.parseValue(buffer, text, JsonSyntax.skipSpace(buffer, text, p, length), length, 1);
            size++;
            p = JsonSyntax.skipComma(buffer, text, JsonSyntax.skipSpace(buffer, text, p, length), length);
        }
        return size;
    }

    /** An integral number as an {@code int}, or {@code 0} when this is not one or it overflows. */
    public int asInt() {
        long value = asLong();
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? 0 : (int) value;
    }

    /** An integral number as a {@code long}, or {@code 0} when this is not one or it overflows. */
    public long asLong() {
        if (type() != Json.TYPE_NUMBER) {
            return 0L;
        }
        int end = end();
        return JsonIntegers.isIntegral(buffer, text, start, end)
                ? JsonIntegers.parseIntegral(buffer, text, start, end) : 0L;
    }

    /**
     * A JSON number, including fractions and exponents, or {@code 0.0} when this is not a number or it overflows a
     * {@code double}.
     */
    public double asDouble() {
        if (type() != Json.TYPE_NUMBER) {
            return 0.0;
        }
        return JsonNumbers.parseDouble(buffer, text, start, end());
    }

    /** A boolean value, or {@code false} when this is not a boolean. */
    public boolean asBoolean() {
        return start >= 0 && JsonChars.at(buffer, text, start) == 't';
    }

    /**
     * A JSON string's content with its escapes decoded, as a new arena {@code String} holding its UTF-8 bytes, or
     * {@code null} when this is not a string. {@link #copyString(byte[])} decodes into a caller buffer instead.
     */
    public @Nullable String asString() {
        if (type() != Json.TYPE_STRING) {
            return null;
        }
        int contentEnd = end() - 1;
        if (!hasEscape(start + 1, contentEnd)) {
            return raw(start + 1, contentEnd);
        }
        StringBuilder decoded = new StringBuilder(contentEnd - start - 1);
        JsonText.decode(buffer, text, start + 1, contentEnd, null, decoded, contentEnd - start - 1);
        return decoded.toString();
    }

    /**
     * Decodes a JSON string, escapes included, to UTF-8 in {@code out} and returns the number of bytes written; {@code 0}
     * when this is not a string. Writes at most {@code out.length} bytes, never part of a character, and no NUL.
     */
    public int copyString(byte[] out) {
        return copyString(out, out.length);
    }

    /** {@link #copyString(byte[])} writing at most {@code capacity} bytes, for instance to keep room for a NUL. */
    public int copyString(byte[] out, int capacity) {
        if (type() != Json.TYPE_STRING || capacity <= 0) {
            return 0;
        }
        return JsonText.decode(buffer, text, start + 1, end() - 1, out, null, capacity);
    }

    /**
     * This value's JSON text exactly as the document writes it, as a new arena {@code String}: a number keeps every
     * digit ({@code "21.2"}), a string keeps its quotes and escapes, an object or array its members. Empty for a missing
     * value or an invalid document.
     */
    @Override
    public String toString() {
        if (start < 0) {
            return "";
        }
        String copy = raw(start, end());
        return copy == null ? "" : copy;
    }

    private int end() {
        return JsonSyntax.valueEnd(buffer, text, start, length);
    }

    private JsonValue at(int valueStart) {
        return new JsonValue(buffer, text, length, valueStart);
    }

    private boolean hasEscape(int from, int to) {
        for (int i = from; i < to; i++) {
            if (JsonChars.at(buffer, text, i) == '\\') {
                return true;
            }
        }
        return false;
    }

    /** The document's bytes {@code [from, to)} as a new arena {@code String}, sized to them. */
    private @Nullable String raw(int from, int to) {
        byte[] bytes = buffer;
        if (bytes != null) {
            return Json.rawText(bytes, from, to);
        }
        String document = text;
        if (document == null) {
            return null;
        }
        StringBuilder copy = new StringBuilder(to - from);
        for (int i = from; i < to; i++) {
            copy.append(document.charAt(i));
        }
        return copy.toString();
    }
}
