package io.github.jabrena.juno.jdk.net.http;

/**
 * A request's header lines, kept as the bytes the board sends: {@code Name: value\r\n} each, in a buffer of
 * {@value HttpRequest#HEADERS_CAPACITY} bytes. Names and values are checked as the JDK checks them, with the board's
 * ASCII-only {@code String} methods.
 */
final class RequestHeaders {
    /** The headers the client sets itself, which a request may not, as in the JDK. */
    private static final String[] RESTRICTED = {"connection", "content-length", "expect", "host", "upgrade"};
    /** The characters of an HTTP token besides letters and digits. */
    private static final String TOKEN_SYMBOLS = "!#$%&'*+-.^_`|~";

    private RequestHeaders() {
    }

    /**
     * Rejects a name that is empty, not a token or restricted, and a value with a line break.
     *
     * @throws IllegalArgumentException for a header the client cannot send
     */
    static void check(String name, String value) {
        if (!isToken(name)) {
            throw new IllegalArgumentException("Invalid header name: " + name);
        }
        for (String restricted : RESTRICTED) {
            if (equalsIgnoreCase(name, restricted)) {
                throw new IllegalArgumentException("Restricted header name: " + name);
            }
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\r' || character == '\n') {
                throw new IllegalArgumentException("Invalid header value for " + name);
            }
        }
    }

    /**
     * Rejects a method that is empty or not a token. Unlike the JDK, {@code CONNECT} is accepted: the board sends it and
     * reports the status, though it opens no tunnel.
     *
     * @throws IllegalArgumentException for a method the client cannot send
     */
    static void checkMethod(String method) {
        if (!isToken(method)) {
            throw new IllegalArgumentException("Invalid method: " + method);
        }
    }

    /**
     * Writes {@code name: value\r\n} into {@code buffer} after its first {@code length} bytes and returns the new
     * length.
     *
     * @throws IllegalArgumentException if the line does not fit in the buffer
     */
    static int append(byte[] buffer, int length, String name, String value) {
        int needed = name.length() + 2 + value.length() + 2;
        if (length + needed > buffer.length) {
            throw new IllegalArgumentException("A request's headers hold at most 512 bytes");
        }
        int index = copy(name, buffer, length);
        buffer[index] = ':';
        buffer[index + 1] = ' ';
        index = copy(value, buffer, index + 2);
        buffer[index] = '\r';
        buffer[index + 1] = '\n';
        return index + 2;
    }

    /** Removes every line called {@code name} from the first {@code length} bytes and returns the new length. */
    static int remove(byte[] buffer, int length, String name) {
        int line = 0;
        int kept = length;
        while (line < kept) {
            int next = line;
            while (next < kept && buffer[next] != '\n') {
                next++;
            }
            next++;
            if (namesLine(buffer, line, kept, name)) {
                for (int i = next; i < kept; i++) {
                    buffer[line + i - next] = buffer[i];
                }
                kept -= next - line;
            } else {
                line = next;
            }
        }
        return kept;
    }

    /** Whether the line at {@code line}, within the first {@code length} bytes, is a {@code name} header. */
    private static boolean namesLine(byte[] buffer, int line, int length, String name) {
        int colon = line + name.length();
        if (colon >= length || buffer[colon] != ':') {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (lowerCase((char) buffer[line + i]) != lowerCase(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int copy(String text, byte[] buffer, int from) {
        for (int i = 0; i < text.length(); i++) {
            buffer[from + i] = (byte) text.charAt(i);
        }
        return from + text.length();
    }

    private static boolean isToken(String text) {
        if (text.length() == 0) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!isTokenCharacter(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTokenCharacter(char character) {
        if (character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9') {
            return true;
        }
        for (int i = 0; i < TOKEN_SYMBOLS.length(); i++) {
            if (TOKEN_SYMBOLS.charAt(i) == character) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsIgnoreCase(String text, String lowerCaseOther) {
        if (text.length() != lowerCaseOther.length()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (lowerCase(text.charAt(i)) != lowerCaseOther.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static char lowerCase(char character) {
        return character >= 'A' && character <= 'Z' ? (char) (character - 'A' + 'a') : character;
    }
}
