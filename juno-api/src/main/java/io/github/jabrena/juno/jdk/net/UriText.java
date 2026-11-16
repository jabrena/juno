package io.github.jabrena.juno.jdk.net;

/**
 * The character helpers {@link URI} reads and copies its text with. The board's {@code String} has only
 * {@code length()} and {@code charAt(int)}, so each one works character by character.
 */
final class UriText {
    /** A component, or an index, that is not there. */
    static final int NONE = -1;

    private UriText() {
    }

    /** {@code text}'s characters {@code [start, end)} as a new {@code String}, sized to them. */
    static String copy(String text, int start, int end) {
        StringBuilder copy = new StringBuilder(end - start);
        append(copy, text, start, end);
        return copy.toString();
    }

    /** {@code text}'s characters {@code [start, end)} with each {@code %XX} escape decoded to one byte. */
    static String decode(String text, int start, int end) {
        int length = 0;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == '%') {
                i += 2;
            }
            length++;
        }
        StringBuilder decoded = new StringBuilder(length);
        for (int i = start; i < end; i++) {
            char character = text.charAt(i);
            if (character == '%') {
                decoded.append((char) (hexValue(text.charAt(i + 1)) * 16 + hexValue(text.charAt(i + 2))));
                i += 2;
            } else {
                decoded.append(character);
            }
        }
        return decoded.toString();
    }

    static void append(StringBuilder target, String text, int start, int end) {
        for (int i = start; i < end; i++) {
            target.append(text.charAt(i));
        }
    }

    /** Folds {@code text}'s characters {@code [start, end)}, or a mark for a missing component, into {@code seed}. */
    static int hash(int seed, String text, int start, int end, boolean ignoreCase) {
        int hash = 31 * seed + (start == NONE ? 0 : 1);
        for (int i = start; i < end; i++) {
            char character = text.charAt(i);
            hash = 31 * hash + (ignoreCase ? toLower(character) : character);
        }
        return hash;
    }

    /** The first index from {@code from} of any of {@code stops}, or the end of {@code text}. */
    static int indexOfAny(String text, int from, String stops) {
        for (int i = from; i < text.length(); i++) {
            char character = text.charAt(i);
            for (int s = 0; s < stops.length(); s++) {
                if (character == stops.charAt(s)) {
                    return i;
                }
            }
        }
        return text.length();
    }

    static boolean startsWith(String text, int from, String prefix) {
        if (text.length() - from < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (text.charAt(from + i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    static boolean regionMatchesIgnoreCase(String text, int from, String other, int length) {
        for (int i = 0; i < length; i++) {
            if (toLower(text.charAt(from + i)) != toLower(other.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static char toLower(char character) {
        return character >= 'A' && character <= 'Z' ? (char) (character + ('a' - 'A')) : character;
    }

    /** The value of the hexadecimal digit {@code character}, or {@code -1} when it is not one. */
    static int hexValue(char character) {
        if (isDigit(character)) {
            return character - '0';
        }
        char lower = toLower(character);
        return lower >= 'a' && lower <= 'f' ? lower - 'a' + 10 : -1;
    }

    static boolean isAlpha(char character) {
        char lower = toLower(character);
        return lower >= 'a' && lower <= 'z';
    }

    static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
