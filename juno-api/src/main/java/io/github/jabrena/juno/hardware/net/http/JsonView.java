package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/**
 * One value inside a JSON document, navigated with JEP 540's method names ({@code get(String)}, {@code get(int)},
 * {@code asInt()}, ...). A view is the document plus the index where its value starts: it never copies the document or
 * parses it into a tree. {@link Json#view(String)} validates the whole document once, so each later step only scans
 * the value it starts from.
 *
 * <pre>{@code
 * JsonView puzzle = Json.view(response.body()).get("puzzle");
 * int rating = puzzle.get("rating").asInt();
 * JsonView solution = puzzle.get("solution");
 * for (int i = 0; i < solution.size(); i++) {
 *     int length = solution.get(i).copyString(uci);
 * }
 * }</pre>
 *
 * <p>Nothing throws: a missing member, an index out of range, a step into a non-container or an invalid document gives
 * a view whose {@link #type()} says so, every later step keeps that answer, and the {@code as*} conversions return zero
 * values. Integer conversions accept only integral literals ({@code 123}, not {@code 123.0}) and return {@code 0} on
 * overflow.
 *
 * <p>Each {@code get} returns a new view, one 20-byte arena object that the collector reclaims; keep a view in a local
 * variable rather than repeating a step. A view reads the document where it is, so a {@code byte[]} buffer must not be
 * reused for another document while its views are still read.
 */
public final class JsonView {
    private final byte @Nullable [] buffer;
    private final @Nullable String text;
    private final int length;
    /** Where the value starts, or {@link JsonLookup#MISSING} or {@link JsonLookup#INVALID}. */
    private final int start;

    JsonView(byte @Nullable [] buffer, @Nullable String text, int length, int start) {
        this.buffer = buffer;
        this.text = text;
        this.length = length;
        this.start = start;
    }

    /** The member {@code name} of this object; a missing view when absent or when this is not an object. */
    public JsonView get(String name) {
        if (start < 0) {
            return this;
        }
        if (JsonChars.at(buffer, text, start) != '{') {
            return at(JsonLookup.MISSING);
        }
        int member = JsonLookup.member(buffer, text, start, end(), name);
        return at(member == JsonSyntax.FAILED ? JsonLookup.MISSING : member);
    }

    /** The element {@code index} of this array; a missing view when out of range or when this is not an array. */
    public JsonView get(int index) {
        if (start < 0) {
            return this;
        }
        if (JsonChars.at(buffer, text, start) != '[' || index < 0) {
            return at(JsonLookup.MISSING);
        }
        int element = JsonLookup.element(buffer, text, start, end(), index);
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

    /** Whether the document this view came from is malformed or truncated. */
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
        int end = end();
        int p = JsonSyntax.skipSpace(buffer, text, start + 1, end);
        int size = 0;
        while (p < end - 1) {
            if (type == Json.TYPE_OBJECT) {
                p = JsonSyntax.skipSpace(buffer, text, JsonText.parseString(buffer, text, p, end), end) + 1;
            }
            p = JsonSyntax.parseValue(buffer, text, JsonSyntax.skipSpace(buffer, text, p, end), end, 1);
            size++;
            p = JsonSyntax.skipComma(buffer, text, JsonSyntax.skipSpace(buffer, text, p, end), end);
        }
        return size;
    }

    /** A strictly integral 32-bit value, or {@code 0} when this is not one or it overflows. */
    public int asInt() {
        long value = asLong();
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? 0 : (int) value;
    }

    /** A strictly integral 64-bit value, or {@code 0} when this is not one or it overflows. */
    public long asLong() {
        if (type() != Json.TYPE_NUMBER) {
            return 0L;
        }
        int end = end();
        return JsonNumbers.isInteger(buffer, text, start, end) ? JsonNumbers.parseLong(buffer, text, start, end) : 0L;
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
     * The raw JSON text of a scalar as a new arena {@code String}: a string's content without its quotes (escapes are
     * not decoded), or a number, boolean or {@code null} literal exactly as written, which keeps a number's precision.
     * {@code null} for an object, an array or a missing value.
     */
    public @Nullable String asString() {
        int type = type();
        if (type == Json.TYPE_MISSING || type == Json.TYPE_INVALID || type == Json.TYPE_OBJECT
                || type == Json.TYPE_ARRAY) {
            return null;
        }
        int end = end();
        int from = type == Json.TYPE_STRING ? start + 1 : start;
        int to = type == Json.TYPE_STRING ? end - 1 : end;
        byte[] bytes = buffer;
        if (bytes != null) {
            return Json.rawText(bytes, from, to);
        }
        String document = text;
        return document == null ? null : copy(document, from, to);
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
        return JsonText.decode(buffer, text, start + 1, end() - 1, out, capacity);
    }

    private int end() {
        return JsonSyntax.valueEnd(buffer, text, start, length);
    }

    private JsonView at(int valueStart) {
        return new JsonView(buffer, text, length, valueStart);
    }

    /** {@code document}'s characters {@code [from, to)} as a new arena {@code String}, sized to them. */
    private static String copy(String document, int from, int to) {
        StringBuilder copy = new StringBuilder(to - from);
        for (int i = from; i < to; i++) {
            copy.append(document.charAt(i));
        }
        return copy.toString();
    }
}
