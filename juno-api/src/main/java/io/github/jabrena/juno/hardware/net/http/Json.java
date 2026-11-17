package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/**
 * Entry point of Juno's JSON support, shaped after JDK 28's {@code jdk.incubator.json} (JEP 540): {@link #view}
 * validates a document once and returns a {@link JsonView} of its root, which is navigated with {@code get(String)}
 * and {@code get(int)} and read with {@code asInt()}, {@code asDouble()}, {@code copyString(...)} and similar
 * methods. {@link JsonWriter} produces JSON. The document is read where it is, from a {@code java.net.http} response
 * body held in a {@code String} or from a {@code byte[]} buffer such as an {@link HttpServer} request body; there is
 * no parsed tree.
 *
 * <p>Unlike JEP 540 nothing throws: a missing member, a type mismatch or an invalid document gives a view whose
 * {@link JsonView#type()} says so, and its conversions return zero values. The scanner validates strictly (RFC 8259,
 * nesting up to 32 levels), but the first of duplicate member names wins and escaped member names are not decoded
 * while matching.
 *
 * <p>JSON needs the UNO R4 WiFi or a larger board: the compiler rejects it on the UNO R3 and the UNO WiFi Rev2. Each
 * {@link JsonView#get} step allocates one small view (20 bytes) that the collector reclaims.
 */
public final class Json {
    /** {@link JsonView#type()}: no value exists at that step. */
    public static final int TYPE_MISSING = 0;
    /** {@link JsonView#type()}: the literal {@code null}. */
    public static final int TYPE_NULL = 1;
    /** {@link JsonView#type()}: {@code true} or {@code false}. */
    public static final int TYPE_BOOLEAN = 2;
    /** {@link JsonView#type()}: a number. */
    public static final int TYPE_NUMBER = 3;
    /** {@link JsonView#type()}: a string. */
    public static final int TYPE_STRING = 4;
    /** {@link JsonView#type()}: an object. */
    public static final int TYPE_OBJECT = 5;
    /** {@link JsonView#type()}: an array. */
    public static final int TYPE_ARRAY = 6;
    /** {@link JsonView#type()}: the document is malformed or truncated. */
    public static final int TYPE_INVALID = 7;

    private Json() {
    }

    /** Validates {@code json} once and returns a view of its root value, invalid when the document is malformed. */
    public static JsonView view(String json) {
        return new JsonView(null, json, json.length(), JsonLookup.root(null, json, json.length()));
    }

    /** {@link #view(String)} for the first {@code bufferLength} bytes of {@code buffer}. */
    public static JsonView view(byte[] buffer, int bufferLength) {
        return new JsonView(buffer, null, bufferLength, JsonLookup.root(buffer, null, bufferLength));
    }

    /**
     * Copies {@code buffer[start, end)} into a new arena {@code String}.
     * The only part of the JSON support that is a compiler intrinsic: Java has no allocation-free way to make a String.
     */
    static native @Nullable String rawText(byte[] buffer, int start, int end);
}
