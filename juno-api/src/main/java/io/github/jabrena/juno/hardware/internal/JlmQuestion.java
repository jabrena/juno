package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.LanguageModel;

/**
 * A question as the model was trained to read it: at most {@link #MAX_LENGTH} characters, in lower case, with
 * {@code ? ! , ;}, quotes and non-printable characters as spaces, runs of spaces collapsed and none at either end. The
 * buffer is allocated once with its engine.
 */
final class JlmQuestion {
    static final int MAX_LENGTH = LanguageModel.MAX_QUESTION;
    private static final char FIRST = ' ';
    private static final char LAST = '~';

    private final char[] text = new char[MAX_LENGTH];
    private int length;

    /** Normalizes the first characters of {@code question}, up to {@link #MAX_LENGTH}. */
    void read(String question) {
        length = 0;
        for (int i = 0; i < question.length() && i < MAX_LENGTH; i++) {
            append(question.charAt(i));
        }
        trim();
    }

    /** Normalizes {@code question[0..size)}, stopping at a zero byte and after {@link #MAX_LENGTH} bytes. */
    void read(byte[] question, int size) {
        length = 0;
        for (int i = 0; i < size && i < MAX_LENGTH && question[i] != 0; i++) {
            append((char) (question[i] & 0xFF));
        }
        trim();
    }

    private void append(char raw) {
        char c = raw < FIRST || raw > LAST || raw == '?' || raw == '!' || raw == ',' || raw == ';' || raw == '"'
                ? ' ' : raw;
        if (c >= 'A' && c <= 'Z') {
            c = (char) (c + ('a' - 'A'));
        }
        if (c == ' ' && (length == 0 || text[length - 1] == ' ')) {
            return;
        }
        text[length] = c;
        length++;
    }

    private void trim() {
        while (length > 0 && text[length - 1] == ' ') {
            length--;
        }
    }

    int length() {
        return length;
    }

    char charAt(int index) {
        return text[index];
    }
}
