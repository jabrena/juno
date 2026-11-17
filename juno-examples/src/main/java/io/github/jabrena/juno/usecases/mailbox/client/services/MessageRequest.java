package io.github.jabrena.juno.usecases.mailbox.client.services;

/** The JSON body of a new mailbox message. */
public final class MessageRequest {
    /** The longest body, in bytes, the mailbox board accepts. */
    private static final int CAPACITY = 430;

    /** Encodes {@code {"title":...,"body":...}}, or returns {@code null} when it is longer than the board accepts. */
    String encode(char[] title, int titleLength, char[] body, int bodyLength) {
        int length = 0;
        StringBuilder json = new StringBuilder(CAPACITY);
        length = appendAscii(json, length, "{\"title\":\"");
        length = appendJson(json, length, title, titleLength);
        length = appendAscii(json, length, "\",\"body\":\"");
        length = appendJson(json, length, body, bodyLength);
        length = appendAscii(json, length, "\"}");
        return length < 0 ? null : json.toString();
    }

    private int appendJson(StringBuilder json, int length, char[] value, int valueLength) {
        int position = length;
        for (int index = 0; index < valueLength; index++) {
            char character = value[index];
            if (character == '"' || character == '\\') {
                position = append(json, position, '\\');
            } else if (character == '\n') {
                position = appendAscii(json, position, "\\n");
                continue;
            } else if (character == '\r') {
                position = appendAscii(json, position, "\\r");
                continue;
            }
            position = append(json, position, character);
        }
        return position;
    }

    private int appendAscii(StringBuilder json, int length, String value) {
        int position = length;
        for (int index = 0; index < value.length(); index++) {
            position = append(json, position, value.charAt(index));
        }
        return position;
    }

    /** Appends {@code character} and returns the new length, or -1 once the body would pass {@link #CAPACITY}. */
    private int append(StringBuilder json, int length, char character) {
        if (length < 0 || length >= CAPACITY) {
            return -1;
        }
        json.append(character);
        return length + 1;
    }
}
