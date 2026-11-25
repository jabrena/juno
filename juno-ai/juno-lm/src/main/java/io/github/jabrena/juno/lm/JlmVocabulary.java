package io.github.jabrena.juno.lm;

/**
 * The tokens of a JLM1 model: {@link #PAD}, {@link #END} (the separator after a question and the end of an answer),
 * and one token per character of the model's alphabet, from 2 up, in alphabet order. The alphabet is printable ASCII
 * and always holds the space, which stands in for any character the alphabet lacks. A JLM1 file carries its alphabet
 * in the header; {@link #ASCII} is the whole printable range, the alphabet the trainer uses.
 */
public final class JlmVocabulary {
    public static final int PAD = 0;
    public static final int END = 1;
    /** Longest question the board engine reads; longer text is cut. */
    public static final int MAX_QUESTION = 72;
    /** Tokens before the alphabet's first character. */
    public static final int RESERVED = 2;
    /** Every printable ASCII character, {@code ' '} to {@code '~'}: 97 tokens. */
    public static final JlmVocabulary ASCII = new JlmVocabulary(printableAscii());

    private static final char FIRST = ' ';
    private static final char LAST = '~';
    /** Punctuation that never changes what a question asks, so normalization turns it into a space. */
    private static final String SEPARATORS = "?!,;\"";

    private final String alphabet;
    /** The token of each printable character, indexed by {@code character - ' '}; 0 for one outside the alphabet. */
    private final int[] tokens = new int[LAST - FIRST + 1];

    /**
     * A vocabulary over {@code alphabet}: distinct printable ASCII characters, including the space.
     *
     * @throws JlmFormatException for an alphabet the board engine would refuse
     */
    public JlmVocabulary(String alphabet) {
        if (alphabet.indexOf(FIRST) < 0) {
            throw new JlmFormatException("alphabet must contain the space");
        }
        for (int i = 0; i < alphabet.length(); i++) {
            char character = alphabet.charAt(i);
            if (!isPrintable(character)) {
                throw new JlmFormatException("alphabet character %d is not printable ASCII".formatted((int) character));
            }
            if (tokens[character - FIRST] != 0) {
                throw new JlmFormatException("alphabet repeats '%c'".formatted(character));
            }
            tokens[character - FIRST] = i + RESERVED;
        }
        this.alphabet = alphabet;
    }

    private static String printableAscii() {
        StringBuilder characters = new StringBuilder();
        for (char c = FIRST; c <= LAST; c++) {
            characters.append(c);
        }
        return characters.toString();
    }

    /** The characters of tokens 2 and up, in token order. */
    public String alphabet() {
        return alphabet;
    }

    /** Number of tokens: {@link #RESERVED} plus the alphabet. */
    public int size() {
        return RESERVED + alphabet.length();
    }

    /** The token of a character; anything outside the alphabet becomes a space, as on the board. */
    public int token(char character) {
        int token = isPrintable(character) ? tokens[character - FIRST] : 0;
        return token != 0 ? token : tokens[0];
    }

    /** The character of a token from {@link #RESERVED} to {@code size() - 1}. */
    public char character(int token) {
        if (token < RESERVED || token >= size()) {
            throw new JlmFormatException("token %d is not a character".formatted(token));
        }
        return alphabet.charAt(token - RESERVED);
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
        int[] result = new int[text.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = token(text.charAt(i));
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JlmVocabulary vocabulary && alphabet.equals(vocabulary.alphabet);
    }

    @Override
    public int hashCode() {
        return alphabet.hashCode();
    }

    @Override
    public String toString() {
        return "JlmVocabulary[" + size() + " tokens]";
    }
}
