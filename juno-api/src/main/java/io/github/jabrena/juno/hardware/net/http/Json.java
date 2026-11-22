package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/**
 * Entry point of Juno's JSON support, after JDK 28's {@code jdk.incubator.json} (JEP 540): {@link #parse} validates a
 * document once and returns its root {@link JsonValue}, navigated with {@code get(String)} and {@code get(int)} and read
 * with {@code asInt()}, {@code asDouble()}, {@code asString()} and similar methods; {@link JsonWriter} produces JSON.
 * The document is read where it is, from a {@code java.net.http} response body held in a {@code String} or from a
 * {@code byte[]} buffer such as a server's request body; there is no parsed tree.
 *
 * <p>Unlike JEP 540 nothing throws: a missing member, a type mismatch or an invalid document gives a value whose
 * {@link JsonValue#type()} says so, and its conversions return zero values or {@code null}. The scanner validates
 * strictly (RFC 8259, nesting up to 32 levels), but the first of duplicate member names wins and escaped member names
 * are not decoded while matching.
 *
 * <p>JSON needs the UNO R4 WiFi or a larger board: the compiler rejects it on the UNO R3. Each
 * {@link JsonValue#get} step allocates one small value (20 bytes) that the collector reclaims.
 */
public final class Json {
    /** {@link JsonValue#type()}: no value exists at that step. */
    public static final int TYPE_MISSING = 0;
    /** {@link JsonValue#type()}: the literal {@code null}. */
    public static final int TYPE_NULL = 1;
    /** {@link JsonValue#type()}: {@code true} or {@code false}. */
    public static final int TYPE_BOOLEAN = 2;
    /** {@link JsonValue#type()}: a number. */
    public static final int TYPE_NUMBER = 3;
    /** {@link JsonValue#type()}: a string. */
    public static final int TYPE_STRING = 4;
    /** {@link JsonValue#type()}: an object. */
    public static final int TYPE_OBJECT = 5;
    /** {@link JsonValue#type()}: an array. */
    public static final int TYPE_ARRAY = 6;
    /** {@link JsonValue#type()}: the document is malformed or truncated. */
    public static final int TYPE_INVALID = 7;

    private Json() {
    }

    /** Validates {@code json} once and returns its root value, invalid when the document is malformed. */
    public static JsonValue parse(String json) {
        return new JsonValue(null, json, json.length(), JsonLookup.root(null, json, json.length()));
    }

    /** {@link #parse(String)} for the first {@code bufferLength} bytes of {@code buffer}. */
    public static JsonValue parse(byte[] buffer, int bufferLength) {
        return new JsonValue(buffer, null, bufferLength, JsonLookup.root(buffer, null, bufferLength));
    }

    /**
     * Copies {@code buffer[start, end)} into a new arena {@code String}.
     * The only part of the JSON support that is a compiler intrinsic: Java has no allocation-free way to make a String.
     */
    static native @Nullable String rawText(byte[] buffer, int start, int end);
}
