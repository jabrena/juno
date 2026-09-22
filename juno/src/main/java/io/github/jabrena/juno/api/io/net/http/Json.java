package io.github.jabrena.juno.api.io.net.http;

/**
 * A bounded, non-allocating JSON field reader recognized as compiler intrinsics by Juno. Reads
 * directly out of a {@code byte[]} buffer (typically one filled by {@link HttpClient}) without
 * ever building a parsed document — Juno has no heap, so there is no DOM to build.
 *
 * <p>{@code path} must be a compile-time string literal. Object fields use dot notation and arrays
 * use zero-based bracket indexes, for example {@code "data.sensor.temp"},
 * {@code "users[0].name"}, or {@code "[2]"} for a root array. The empty path addresses the root
 * value. Field names containing {@code '.'}, {@code '['}, or {@code ']'} cannot currently be
 * addressed, and escaped JSON member names are not decoded while matching paths.
 *
 * <p>The scanner validates the complete bounded document before returning a value. Call
 * {@link #type} when a missing value, JSON {@code null}, a type mismatch, and a malformed or
 * truncated document must be distinguished. Typed getters retain convenient zero-value failure
 * results and never allocate or throw. {@code bufferLength} must be the number of JSON bytes in
 * the buffer (for example, the value returned by {@link HttpClient#get}), not the buffer capacity.
 *
 * <p>{@link #getString(byte[], int, String, byte[], int)} decodes JSON escapes, including UTF-16
 * <code>&#92;uXXXX</code> surrogate pairs, to UTF-8 in {@code out}. It writes at most
 * {@code outLength} bytes and never appends a NUL byte.
 *
 * <p>{@link #getString(byte[], int, String)} instead returns a bounded runtime {@link String}
 * holding the value's raw JSON text (quotes stripped for an actual JSON string, no escape
 * decoding) — useful for a JSON number when its exact source text matters more than its value
 * parsed through {@link #getDouble}, which loses precision for values not exactly representable
 * in binary floating point.
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
    public static native int type(byte[] buffer, int bufferLength, String path);

    /** Returns a strictly integral 32-bit value, or {@code 0} on failure or overflow. */
    public static native int getInt(byte[] buffer, int bufferLength, String path);

    /** Returns a strictly integral 64-bit value, or {@code 0} on failure or overflow. */
    public static native long getLong(byte[] buffer, int bufferLength, String path);

    /** Returns a JSON number, including fractions and exponents, or {@code 0.0} on failure. */
    public static native double getDouble(byte[] buffer, int bufferLength, String path);

    /** Returns a boolean value, or {@code false} when the value is absent or not a boolean. */
    public static native boolean getBool(byte[] buffer, int bufferLength, String path);

    /** Decodes a JSON string to UTF-8 and returns the number of bytes written. */
    public static native int getString(byte[] buffer, int bufferLength, String path,
            byte[] out, int outLength);

    /**
     * Returns the raw JSON text of any scalar value at {@code path} (a JSON string's content with
     * its surrounding quotes stripped, or a number/boolean/{@code null} literal exactly as
     * written), or {@code null} when the path is missing, resolves to an object or array, or the
     * raw text is too long for Juno's bounded runtime-string pool.
     */
    public static native String getString(byte[] buffer, int bufferLength, String path);

    /** Returns the number of direct array elements, or {@code -1} on failure or a type mismatch. */
    public static native int arraySize(byte[] buffer, int bufferLength, String path);
}
