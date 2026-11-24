package io.github.jabrena.juno.tny2;

/**
 * The 97 TNY2 tokens: {@link #PAD}, {@link #END} (the separator after a question and the end of an answer), and the
 * printable ASCII characters {@code ' '} to {@code '~'} as {@code character - 30}.
 */
public final class Tny2Vocabulary {
    public static final int SIZE = 97;
    public static final int PAD = 0;
    public static final int END = 1;
    /** Longest question the board engine reads; longer text is cut. */
    public static final int MAX_QUESTION = 72;
    public static final Tny2Vocabulary ASCII = new Tny2Vocabulary();

    private static final int OFFSET = 30;
    private static final char FIRST = ' ';
    private static final char LAST = '~';
    /** Punctuation that never changes what a question asks, so normalization turns it into a space. */
    private static final String SEPARATORS = "?!,;\"";

    private Tny2Vocabulary() {
    }

    /** The token of a character; anything outside printable ASCII becomes a space, as on the board. */
    public int token(char character) {
        return (isPrintable(character) ? character : FIRST) - OFFSET;
    }

    /** The character of a token from 2 to 96. */
    public char character(int token) {
        if (token <= END || token >= SIZE) {
            throw new Tny2FormatException("token %d is not a character".formatted(token));
        }
        return (char) (token + OFFSET);
    }

    public boolean isPrintable(char character) {
        return character >= FIRST && character <= LAST;
    }

    /**
     * Normalizes a question the way the board engine does, so wordings that differ only in case or punctuation reach
     * the model as one input: the text is cut to {@link #MAX_QUESTION} characters, upper-case letters become lower
     * case, non-printable characters and {@code ? ! , ; "} become spaces, and runs of spaces collapse to one, with
     * none at either end.
     */
    public String question(String text) {
        StringBuilder cleaned = new StringBuilder(Math.min(text.length(), MAX_QUESTION));
        for (int i = 0; i < text.length() && i < MAX_QUESTION; i++) {
            char character = normalized(text.charAt(i));
            boolean space = character == FIRST;
            if (space && (cleaned.isEmpty() || cleaned.charAt(cleaned.length() - 1) == FIRST)) {
                continue;
            }
            cleaned.append(character);
        }
        int end = cleaned.length();
        while (end > 0 && cleaned.charAt(end - 1) == FIRST) {
            end--;
        }
        return cleaned.substring(0, end);
    }

    private char normalized(char character) {
        if (!isPrintable(character) || SEPARATORS.indexOf(character) >= 0) {
            return FIRST;
        }
        return character >= 'A' && character <= 'Z' ? (char) (character + ('a' - 'A')) : character;
    }

    /** The tokens of a text, one per character. */
    public int[] tokens(String text) {
        int[] tokens = new int[text.length()];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = token(text.charAt(i));
        }
        return tokens;
    }
}
