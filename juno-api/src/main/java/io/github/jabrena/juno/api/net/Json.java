package io.github.jabrena.juno.api.net;

/**
 * A bounded, non-allocating JSON field reader recognized as compiler intrinsics by Juno. Reads
 * directly out of a {@code byte[]} buffer (typically one filled by {@link HttpClient}) without
 * ever building a parsed document — Juno has no heap, so there is no DOM to build.
 *
 * <p>{@code key} must be a compile-time string literal, in dot-path form (e.g. {@code "temp"} for
 * a top-level field, or {@code "data.sensor.temp"} to reach two levels of nesting). Only JSON
 * objects can be traversed this way, not arrays. A missing key, a type mismatch, or a key path
 * that doesn't resolve returns {@code 0}/{@code false}/an empty buffer rather than throwing —
 * check the returned value against your own expectations if that distinction matters.
 *
 * <p>{@link #getString} copies the field's raw characters (no escape processing) into
 * {@code out}, truncated to {@code outLength} bytes, and returns the number of bytes written.
 */
public final class Json {
    private Json() {
    }

    public static native int getInt(byte[] buffer, int bufferLength, String key);

    public static native boolean getBool(byte[] buffer, int bufferLength, String key);

    public static native int getString(byte[] buffer, int bufferLength, String key,
            byte[] out, int outLength);
}
