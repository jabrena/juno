package io.github.jabrena.juno.jdk.net;

import static io.github.jabrena.juno.jdk.net.UriText.NONE;
import static io.github.jabrena.juno.jdk.net.UriText.hexValue;
import static io.github.jabrena.juno.jdk.net.UriText.isAlpha;
import static io.github.jabrena.juno.jdk.net.UriText.isDigit;

/** Finds where {@link URI}'s components start and end, and rejects a text that is not a URI. */
final class UriSyntax {
    private static final int MAX_PORT = 65_535;

    private UriSyntax() {
    }

    /** Rejects a character no URI may hold, a second {@code #}, and a {@code %} not followed by two hex digits. */
    static void validateCharacters(String text) {
        boolean fragment = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (isIllegal(character)) {
                throw new IllegalArgumentException("Illegal character in URI at index " + i + ": " + text);
            }
            if (character == '#') {
                if (fragment) {
                    throw new IllegalArgumentException("Illegal character in fragment at index " + i + ": " + text);
                }
                fragment = true;
            }
            if (character == '%' && (i + 2 >= text.length() || hexValue(text.charAt(i + 1)) < 0
                    || hexValue(text.charAt(i + 2)) < 0)) {
                throw new IllegalArgumentException("Malformed escape pair at index " + i + ": " + text);
            }
        }
    }

    /** Where the scheme's {@code :} is, or {@link UriText#NONE} when {@code text} does not start with a scheme. */
    static int schemeEnd(String text) {
        if (text.length() == 0 || !isAlpha(text.charAt(0))) {
            return NONE;
        }
        for (int i = 1; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == ':') {
                return i;
            }
            if (!isAlpha(character) && !isDigit(character) && character != '+' && character != '-'
                    && character != '.') {
                return NONE;
            }
        }
        return NONE;
    }

    /** Where the host starts: after the last {@code @} of the authority, which ends the user info. */
    static int hostStart(String text, int authorityStart, int authorityEnd) {
        int start = authorityStart;
        for (int i = authorityStart; i < authorityEnd; i++) {
            if (text.charAt(i) == '@') {
                start = i + 1;
            }
        }
        return start;
    }

    /** Where the host ends: after an IPv6 literal's {@code ]}, else at the {@code :} before a port or the end. */
    static int hostEnd(String text, int hostStart, int authorityEnd) {
        int end = hostStart;
        if (end < authorityEnd && text.charAt(end) == '[') {
            while (end < authorityEnd && text.charAt(end) != ']') {
                end++;
            }
            if (end == authorityEnd) {
                throw new IllegalArgumentException("Expected ] to close an IPv6 address: " + text);
            }
            return end + 1;
        }
        while (end < authorityEnd && text.charAt(end) != ':') {
            end++;
        }
        return end;
    }

    /** Where the fragment starts, just after {@code #}, or {@link UriText#NONE}. */
    static int fragmentStart(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) == '#') {
                return i + 1;
            }
        }
        return NONE;
    }

    /** The port written between {@code start} and {@code end}; none when that is empty, as in the JDK. */
    static int parsePort(String text, int start, int end) {
        if (start == end) {
            return NONE;
        }
        int value = 0;
        for (int i = start; i < end; i++) {
            char digit = text.charAt(i);
            if (!isDigit(digit)) {
                throw new IllegalArgumentException("The port must be a number: " + text);
            }
            value = value * 10 + (digit - '0');
            if (value > MAX_PORT) {
                throw new IllegalArgumentException("The port must be at most 65535: " + text);
            }
        }
        return value;
    }

    private static boolean isIllegal(char character) {
        return switch (character) {
            case '"', '<', '>', '\\', '^', '`', '{', '|', '}', 0x7F -> true;
            default -> character <= ' ';
        };
    }
}
