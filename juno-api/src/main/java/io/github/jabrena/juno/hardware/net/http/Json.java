package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/**
 * A bounded, non-allocating JSON field reader. Reads directly out of a {@code java.net.http} response body held in a
 * {@code String}, or a {@code byte[]} buffer such as an {@link HttpServer} request body, without ever building a parsed
 * document — Juno has no heap, so there is no DOM to build. The
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
 * JSON bytes in the buffer (for example, the value returned by {@link HttpServer#accept}), not the buffer capacity.
 *
 * <p>{@link #getString(byte[], int, String, byte[], int)} decodes JSON escapes, including UTF-16
 * <code>&#92;uXXXX</code> surrogate pairs, to UTF-8 in {@code out}. It writes at most {@code outLength} bytes and
 * never appends a NUL byte.
 *
 * <p>Every method also takes the document as a {@code String}, such as a {@code java.net.http} response body read with
 * {@code BodyHandlers.ofString()}: {@code Json.getDouble(reply.body(), "current.temperature_2m")}. The same scanner
 * reads both; a runtime {@code String} holds bytes, so it is read as the bytes it holds.
 *
 * <p>{@link #getString(byte[], int, String)} instead returns a runtime {@link String} holding the value's raw
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
        return typeOf(buffer, null, bufferLength, path);
    }

    /** {@link #type(byte[], int, String)} for a document held in a {@code String}. */
    public static int type(String json, String path) {
        return typeOf(null, json, json.length(), path);
    }

    /** Returns a strictly integral 32-bit value, or {@code 0} on failure or overflow. */
    public static int getInt(byte[] buffer, int bufferLength, String path) {
        return toInt(longOf(buffer, null, bufferLength, path));
    }

    /** {@link #getInt(byte[], int, String)} for a document held in a {@code String}. */
    public static int getInt(String json, String path) {
        return toInt(longOf(null, json, json.length(), path));
    }

    /** Returns a strictly integral 64-bit value, or {@code 0} on failure or overflow. */
    public static long getLong(byte[] buffer, int bufferLength, String path) {
        return longOf(buffer, null, bufferLength, path);
    }

    /** {@link #getLong(byte[], int, String)} for a document held in a {@code String}. */
    public static long getLong(String json, String path) {
        return longOf(null, json, json.length(), path);
    }

    /** Returns a JSON number, including fractions and exponents, or {@code 0.0} on failure. */
    public static double getDouble(byte[] buffer, int bufferLength, String path) {
        return doubleOf(buffer, null, bufferLength, path);
    }

    /** {@link #getDouble(byte[], int, String)} for a document held in a {@code String}. */
    public static double getDouble(String json, String path) {
        return doubleOf(null, json, json.length(), path);
    }

    /** Returns a boolean value, or {@code false} when the value is absent or not a boolean. */
    public static boolean getBool(byte[] buffer, int bufferLength, String path) {
        return boolOf(buffer, null, bufferLength, path);
    }

    /** {@link #getBool(byte[], int, String)} for a document held in a {@code String}. */
    public static boolean getBool(String json, String path) {
        return boolOf(null, json, json.length(), path);
    }

    /** Decodes a JSON string to UTF-8 and returns the number of bytes written. */
    public static int getString(byte[] buffer, int bufferLength, String path, byte[] out, int outLength) {
        return decodedOf(buffer, null, bufferLength, path, out, outLength);
    }

    /** {@link #getString(byte[], int, String, byte[], int)} for a document held in a {@code String}. */
    public static int getString(String json, String path, byte[] out, int outLength) {
        return decodedOf(null, json, json.length(), path, out, outLength);
    }

    /**
     * Returns the raw JSON text of any scalar value at {@code path} (a JSON string's content with its surrounding
     * quotes stripped, or a number/boolean/{@code null} literal exactly as written), or {@code null} when the path is
     * missing or resolves to an object or array. The text is copied into a new arena {@code String}.
     */
    public static @Nullable String getString(byte[] buffer, int bufferLength, String path) {
        int start = JsonPath.locate(buffer, null, bufferLength, path);
        if (start < 0 || buffer[start] == '{' || buffer[start] == '[') {
            return null;
        }
        int end = JsonSyntax.valueEnd(buffer, null, start, bufferLength);
        return buffer[start] == '"' ? rawText(buffer, start + 1, end - 1) : rawText(buffer, start, end);
    }

    /** {@link #getString(byte[], int, String)} for a document held in a {@code String}. */
    public static @Nullable String getString(String json, String path) {
        int start = JsonPath.locate(null, json, json.length(), path);
        if (start < 0 || json.charAt(start) == '{' || json.charAt(start) == '[') {
            return null;
        }
        int end = JsonSyntax.valueEnd(null, json, start, json.length());
        return json.charAt(start) == '"' ? copy(json, start + 1, end - 1) : copy(json, start, end);
    }

    /** Returns the number of direct array elements, or {@code -1} on failure or a type mismatch. */
    public static int arraySize(byte[] buffer, int bufferLength, String path) {
        return arraySizeOf(buffer, null, bufferLength, path);
    }

    /** {@link #arraySize(byte[], int, String)} for a document held in a {@code String}. */
    public static int arraySize(String json, String path) {
        return arraySizeOf(null, json, json.length(), path);
    }

    private static int typeOf(byte @Nullable [] buffer, @Nullable String text, int length, String path) {
        int start = JsonPath.locate(buffer, text, length, path);
        if (start == JsonPath.INVALID) {
            return TYPE_INVALID;
        }
        if (start == JsonPath.MISSING) {
            return TYPE_MISSING;
        }
        int first = JsonChars.at(buffer, text, start);
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

    private static int toInt(long value) {
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? 0 : (int) value;
    }

    private static long longOf(byte @Nullable [] buffer, @Nullable String text, int length, String path) {
        int start = JsonPath.locate(buffer, text, length, path);
        if (start < 0) {
            return 0L;
        }
        int end = JsonSyntax.valueEnd(buffer, text, start, length);
        return JsonNumbers.isInteger(buffer, text, start, end) ? JsonNumbers.parseLong(buffer, text, start, end) : 0L;
    }

    private static double doubleOf(byte @Nullable [] buffer, @Nullable String text, int length, String path) {
        int start = JsonPath.locate(buffer, text, length, path);
        if (start < 0) {
            return 0.0;
        }
        int end = JsonSyntax.valueEnd(buffer, text, start, length);
        return JsonNumbers.parseNumber(buffer, text, start, end) == end
                ? JsonNumbers.parseDouble(buffer, text, start, end) : 0.0;
    }

    private static boolean boolOf(byte @Nullable [] buffer, @Nullable String text, int length, String path) {
        int start = JsonPath.locate(buffer, text, length, path);
        return start >= 0 && JsonSyntax.valueEnd(buffer, text, start, length) - start == 4
                && JsonChars.at(buffer, text, start) == 't';
    }

    private static int decodedOf(byte @Nullable [] buffer, @Nullable String text, int length, String path,
            byte[] out, int outLength) {
        if (out == null || outLength <= 0) {
            return 0;
        }
        int start = JsonPath.locate(buffer, text, length, path);
        if (start < 0 || JsonChars.at(buffer, text, start) != '"') {
            return 0;
        }
        int contentEnd = JsonSyntax.valueEnd(buffer, text, start, length) - 1;
        return JsonText.decode(buffer, text, start + 1, contentEnd, out, outLength);
    }

    private static int arraySizeOf(byte @Nullable [] buffer, @Nullable String text, int length, String path) {
        int start = JsonPath.locate(buffer, text, length, path);
        if (start < 0 || JsonChars.at(buffer, text, start) != '[') {
            return -1;
        }
        int end = JsonSyntax.valueEnd(buffer, text, start, length);
        int p = JsonSyntax.skipSpace(buffer, text, start + 1, end);
        int size = 0;
        while (p < end && JsonChars.at(buffer, text, p) != ']') {
            p = JsonSyntax.parseValue(buffer, text, p, end, 1);
            if (p == JsonSyntax.FAILED) {
                return -1;
            }
            size++;
            p = JsonSyntax.skipComma(buffer, text, JsonSyntax.skipSpace(buffer, text, p, end), end);
        }
        return size;
    }

    /** {@code text}'s characters {@code [start, end)} as a new arena {@code String}, sized to them. */
    private static String copy(String text, int start, int end) {
        StringBuilder copy = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            copy.append(text.charAt(i));
        }
        return copy.toString();
    }

    /**
     * Copies {@code buffer[start, end)} into a new arena {@code String}.
     * The only part of {@code Json} that is a compiler intrinsic: Java has no allocation-free way to make a String.
     */
    private static native @Nullable String rawText(byte[] buffer, int start, int end);
}
