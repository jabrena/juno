package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/**
 * SPIKE (#285): one value inside a JSON document, navigated with JEP 540's method names ({@code get(String)},
 * {@code get(int)}, {@code asInt}, ...) instead of {@link Json}'s path strings. A view is the document plus the index
 * where its value starts; it never copies or parses the document into a tree. {@link Json#view(String)} validates the
 * whole document once, so each later step only scans the value it starts from.
 *
 * <p>Unlike JEP 540 nothing throws: a missing member, a type mismatch or an invalid document gives a view whose
 * {@link #type()} says so, and the {@code as*} conversions return zero values, as {@link Json}'s getters do.
 *
 * <p>Each {@code get} returns a new view: one small arena object per navigation step, reclaimed by the collector. The
 * optimizer cannot remove it, because {@code get} scans the document (a call) and so is never inlined.
 */
public final class JsonView {
    private final byte @Nullable [] buffer;
    private final @Nullable String text;
    private final int length;
    /** Where the value starts, or {@link JsonPath#MISSING} or {@link JsonPath#INVALID}. */
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
            return at(JsonPath.MISSING);
        }
        int end = JsonSyntax.valueEnd(buffer, text, start, length);
        int member = JsonPath.member(buffer, text, start, end, name, 0, name.length());
        return at(member == JsonSyntax.FAILED ? JsonPath.MISSING : member);
    }

    /** The element {@code index} of this array; a missing view when out of range or when this is not an array. */
    public JsonView get(int index) {
        if (start < 0) {
            return this;
        }
        if (JsonChars.at(buffer, text, start) != '[' || index < 0) {
            return at(JsonPath.MISSING);
        }
        int end = JsonSyntax.valueEnd(buffer, text, start, length);
        int element = JsonPath.element(buffer, text, start, end, index);
        return at(element == JsonSyntax.FAILED ? JsonPath.MISSING : element);
    }

    /** One of {@link Json}'s {@code TYPE_*} constants. */
    public int type() {
        if (start == JsonPath.INVALID) {
            return Json.TYPE_INVALID;
        }
        if (start == JsonPath.MISSING) {
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

    public boolean isMissing() {
        return start == JsonPath.MISSING;
    }

    public boolean isNull() {
        return type() == Json.TYPE_NULL;
    }

    public boolean isNumber() {
        return type() == Json.TYPE_NUMBER;
    }

    public boolean isString() {
        return type() == Json.TYPE_STRING;
    }

    /** The number of members of an object or elements of an array, or {@code -1} for any other value. */
    public int size() {
        int type = type();
        if (type != Json.TYPE_OBJECT && type != Json.TYPE_ARRAY) {
            return -1;
        }
        int end = JsonSyntax.valueEnd(buffer, text, start, length);
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

    /** A strictly integral 32-bit value, or {@code 0} on failure or overflow. */
    public int asInt() {
        long value = asLong();
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? 0 : (int) value;
    }

    /** A strictly integral 64-bit value, or {@code 0} on failure or overflow. */
    public long asLong() {
        if (type() != Json.TYPE_NUMBER) {
            return 0L;
        }
        int end = JsonSyntax.valueEnd(buffer, text, start, length);
        return JsonNumbers.isInteger(buffer, text, start, end) ? JsonNumbers.parseLong(buffer, text, start, end) : 0L;
    }

    /** A JSON number, including fractions and exponents, or {@code 0.0} on failure. */
    public double asDouble() {
        if (type() != Json.TYPE_NUMBER) {
            return 0.0;
        }
        return JsonNumbers.parseDouble(buffer, text, start, JsonSyntax.valueEnd(buffer, text, start, length));
    }

    /** A boolean value, or {@code false} when this is not a boolean. */
    public boolean asBoolean() {
        return start >= 0 && JsonChars.at(buffer, text, start) == 't';
    }

    /** Decodes a JSON string to UTF-8 into {@code out} and returns the number of bytes written ({@code 0} if not one). */
    public int copyString(byte[] out) {
        if (type() != Json.TYPE_STRING || out.length == 0) {
            return 0;
        }
        int contentEnd = JsonSyntax.valueEnd(buffer, text, start, length) - 1;
        return JsonText.decode(buffer, text, start + 1, contentEnd, out, out.length);
    }

    private JsonView at(int valueStart) {
        return new JsonView(buffer, text, length, valueStart);
    }
}
