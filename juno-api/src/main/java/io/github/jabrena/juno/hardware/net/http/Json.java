package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/**
 * A bounded, non-allocating JSON field reader. Reads directly out of a {@code byte[]} buffer (typically one filled by
 * {@link HttpClient}) without ever building a parsed document — Juno has no heap, so there is no DOM to build. The
 * scanner is plain Java that Juno compiles into the program only when a {@code Json} method is reachable; only
 * {@link #getString(byte[], int, String)}'s copy into a runtime {@link String} is a compiler intrinsic.
 *
 * <p>Object fields use dot notation and arrays use zero-based bracket indexes, for example
 * {@code "data.sensor.temp"}, {@code "users[0].name"}, or {@code "[2]"} for a root array. The empty path addresses
 * the root value. Field names containing {@code '.'}, {@code '['}, or {@code ']'} cannot currently be addressed, and
 * escaped JSON member names are not decoded while matching paths.
 *
 * <p>The scanner validates the complete bounded document before returning a value. Call {@link #type} when a missing
 * value, JSON {@code null}, a type mismatch, and a malformed or truncated document must be distinguished. Typed getters
 * retain convenient zero-value failure results and never allocate or throw. {@code bufferLength} must be the number of
 * JSON bytes in the buffer (for example, the value returned by {@link HttpClient#get}), not the buffer capacity.
 *
 * <p>{@link #getString(byte[], int, String, byte[], int)} decodes JSON escapes, including UTF-16
 * <code>&#92;uXXXX</code> surrogate pairs, to UTF-8 in {@code out}. It writes at most {@code outLength} bytes and
 * never appends a NUL byte.
 *
 * <p>{@link #getString(byte[], int, String)} instead returns a bounded runtime {@link String} holding the value's raw
 * JSON text (quotes stripped for an actual JSON string, no escape decoding) — useful for a JSON number when its exact
 * source text matters more than its value parsed through {@link #getDouble}, which loses precision for values not
 * exactly representable in binary floating point.
 */
public final class Json {
    public static final int TYPE_MISSING = 0;
    public static final int TYPE_NULL = 1;
    public static final int TYPE_BOOLEAN = 2;
    public static final int TYPE_NUMBER = 3;
    public static final int TYPE_STRING = 4;
    public static final int TYPE_OBJECT = 5;
    public static final int TYPE_ARRAY = 6;
    public static final int TYPE_INVALID = 7;

    private Json() {
    }

    /** Returns one of the {@code TYPE_*} constants for the value at {@code path}. */
    public static int type(byte[] buffer, int bufferLength, String path) {
        int start = JsonPath.locate(buffer, bufferLength, path);
        if (start == JsonPath.INVALID) {
            return TYPE_INVALID;
        }
        if (start == JsonPath.MISSING) {
            return TYPE_MISSING;
        }
        int first = buffer[start];
        if (first == 'n') {
            return TYPE_NULL;
        }
        if (first == 't' || first == 'f') {
            return TYPE_BOOLEAN;
        }
        if (first == '"') {
            return TYPE_STRING;
        }
        if (first == '{') {
            return TYPE_OBJECT;
        }
        return first == '[' ? TYPE_ARRAY : TYPE_NUMBER;
    }

    /** Returns a strictly integral 32-bit value, or {@code 0} on failure or overflow. */
    public static int getInt(byte[] buffer, int bufferLength, String path) {
        long value = getLong(buffer, bufferLength, path);
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? 0 : (int) value;
    }

    /** Returns a strictly integral 64-bit value, or {@code 0} on failure or overflow. */
    public static long getLong(byte[] buffer, int bufferLength, String path) {
        int start = JsonPath.locate(buffer, bufferLength, path);
        if (start < 0) {
            return 0L;
        }
        int end = JsonSyntax.valueEnd(buffer, start, bufferLength);
        return JsonNumbers.isInteger(buffer, start, end) ? JsonNumbers.parseLong(buffer, start, end) : 0L;
    }

    /** Returns a JSON number, including fractions and exponents, or {@code 0.0} on failure. */
    public static double getDouble(byte[] buffer, int bufferLength, String path) {
        int start = JsonPath.locate(buffer, bufferLength, path);
        if (start < 0) {
            return 0.0;
        }
        int end = JsonSyntax.valueEnd(buffer, start, bufferLength);
        return JsonNumbers.parseNumber(buffer, start, end) == end ? JsonNumbers.parseDouble(buffer, start, end) : 0.0;
    }

    /** Returns a boolean value, or {@code false} when the value is absent or not a boolean. */
    public static boolean getBool(byte[] buffer, int bufferLength, String path) {
        int start = JsonPath.locate(buffer, bufferLength, path);
        return start >= 0 && JsonSyntax.valueEnd(buffer, start, bufferLength) - start == 4 && buffer[start] == 't';
    }

    /** Decodes a JSON string to UTF-8 and returns the number of bytes written. */
    public static int getString(byte[] buffer, int bufferLength, String path, byte[] out, int outLength) {
        if (out == null || outLength <= 0) {
            return 0;
        }
        int start = JsonPath.locate(buffer, bufferLength, path);
        if (start < 0 || buffer[start] != '"') {
            return 0;
        }
        int contentEnd = JsonSyntax.valueEnd(buffer, start, bufferLength) - 1;
        return JsonText.decode(buffer, start + 1, contentEnd, out, outLength);
    }

    /**
     * Returns the raw JSON text of any scalar value at {@code path} (a JSON string's content with its surrounding
     * quotes stripped, or a number/boolean/{@code null} literal exactly as written), or {@code null} when the path is
     * missing, resolves to an object or array, or the raw text is too long for Juno's bounded runtime-string pool.
     */
    public static @Nullable String getString(byte[] buffer, int bufferLength, String path) {
        int start = JsonPath.locate(buffer, bufferLength, path);
        if (start < 0 || buffer[start] == '{' || buffer[start] == '[') {
            return null;
        }
        int end = JsonSyntax.valueEnd(buffer, start, bufferLength);
        return buffer[start] == '"' ? rawText(buffer, start + 1, end - 1) : rawText(buffer, start, end);
    }

    /** Returns the number of direct array elements, or {@code -1} on failure or a type mismatch. */
    public static int arraySize(byte[] buffer, int bufferLength, String path) {
        int start = JsonPath.locate(buffer, bufferLength, path);
        if (start < 0 || buffer[start] != '[') {
            return -1;
        }
        int end = JsonSyntax.valueEnd(buffer, start, bufferLength);
        int p = JsonSyntax.skipSpace(buffer, start + 1, end);
        int size = 0;
        while (p < end && buffer[p] != ']') {
            p = JsonSyntax.parseValue(buffer, p, end, 1);
            if (p == JsonSyntax.FAILED) {
                return -1;
            }
            size++;
            p = JsonSyntax.skipComma(buffer, JsonSyntax.skipSpace(buffer, p, end), end);
        }
        return size;
    }

    /**
     * Copies {@code buffer[start, end)} into a runtime-string slot, or returns {@code null} when it does not fit one.
     * The only part of {@code Json} that is a compiler intrinsic: Java has no allocation-free way to make a String.
     */
    private static native @Nullable String rawText(byte[] buffer, int start, int end);
}
