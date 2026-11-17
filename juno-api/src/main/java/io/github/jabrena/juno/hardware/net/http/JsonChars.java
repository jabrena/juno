package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.Nullable;

/**
 * How {@link Json}'s scanner reads a document: from a {@code byte[]} buffer, or from a {@code String} such as a
 * {@code java.net.http} response body. Every scanner method takes both, exactly one of them non-null, so one scanner
 * serves both without allocating a wrapper per call. A runtime {@code String} holds bytes, so each character is read
 * as the byte it is, as from a buffer.
 */
final class JsonChars {

    private JsonChars() {
    }

    /** The byte at {@code index} of {@code buffer}, or else of {@code text}, sign-extended as {@code buffer[index]} is. */
    static int at(byte @Nullable [] buffer, @Nullable String text, int index) {
        if (buffer != null) {
            return buffer[index];
        }
        return text == null ? 0 : (byte) text.charAt(index);
    }
}
