package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.jdk.util.Optional;
import io.github.jabrena.juno.jdk.util.OptionalLong;

/**
 * The board-side {@code java.net.http.HttpHeaders}: a response's headers, read from the client's header buffer. It
 * covers {@code firstValue} and {@code firstValueAsLong}; {@code allValues} and {@code map} need a {@code List} and a
 * {@code Map}, which the board does not have.
 *
 * <p>Names match case-insensitively, as in the JDK. The buffer holds
 * {@value io.github.jabrena.juno.jdk.net.http.HttpClient#RESPONSE_HEADERS_CAPACITY} bytes of headers, in the order the
 * server sent them; a header past that is not found. The buffer is the client's, refilled by every request, so read
 * the headers before the next one.
 */
public final class HttpHeaders {
    private final byte[] block;
    private final int[] statusAndHeadersLength;

    HttpHeaders(byte[] block, int[] statusAndHeadersLength) {
        this.block = block;
        this.statusAndHeadersLength = statusAndHeadersLength;
    }

    /** The value of the first header called {@code name}, without surrounding spaces, if the response has one. */
    public Optional<String> firstValue(String name) {
        int start = valueStart(name);
        if (start < 0) {
            return Optional.empty();
        }
        int end = valueEnd(start);
        // Sized to the value, not to the buffer, so a short value costs the arena only its own bytes.
        StringBuilder value = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            value.append((char) (block[i] & 0xFF));
        }
        return Optional.of(value.toString());
    }

    /**
     * The value of the first header called {@code name} as a {@code long}, if the response has one.
     *
     * @throws NumberFormatException if the value is not a decimal number
     */
    public OptionalLong firstValueAsLong(String name) {
        int start = valueStart(name);
        if (start < 0) {
            return OptionalLong.empty();
        }
        int end = valueEnd(start);
        boolean negative = start < end && block[start] == '-';
        int index = negative ? start + 1 : start;
        if (index == end) {
            throw new NumberFormatException("Not a number: header " + name);
        }
        long number = 0;
        for (; index < end; index++) {
            int digit = block[index] - '0';
            if (digit < 0 || digit > 9) {
                throw new NumberFormatException("Not a number: header " + name);
            }
            number = number * 10 + digit;
        }
        return OptionalLong.of(negative ? -number : number);
    }

    /** How many bytes of the buffer hold headers. */
    private int length() {
        int written = statusAndHeadersLength[1];
        return written < block.length ? written : block.length;
    }

    /** Where the value of the first {@code name} header starts, after its colon and spaces; {@code -1} if none. */
    private int valueStart(String name) {
        int length = length();
        int line = 0;
        while (line < length) {
            int colon = line + name.length();
            if (colon < length && block[colon] == ':' && namesMatch(line, name)) {
                int start = colon + 1;
                while (start < length && block[start] == ' ') {
                    start++;
                }
                return start;
            }
            line = nextLine(line, length);
        }
        return -1;
    }

    /** Where the value starting at {@code start} ends: before its line break and trailing spaces. */
    private int valueEnd(int start) {
        int length = length();
        int end = start;
        while (end < length && block[end] != '\r' && block[end] != '\n') {
            end++;
        }
        while (end > start && block[end - 1] == ' ') {
            end--;
        }
        return end;
    }

    private int nextLine(int line, int length) {
        int index = line;
        while (index < length && block[index] != '\n') {
            index++;
        }
        return index + 1;
    }

    private boolean namesMatch(int line, String name) {
        for (int i = 0; i < name.length(); i++) {
            if (lowerCase(block[line + i]) != lowerCase((byte) name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int lowerCase(byte value) {
        return value >= 'A' && value <= 'Z' ? value - 'A' + 'a' : value;
    }
}
