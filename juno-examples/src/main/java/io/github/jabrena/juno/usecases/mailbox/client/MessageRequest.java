package io.github.jabrena.juno.usecases.mailbox.client;

/** The JSON body of a new mailbox message, encoded into one buffer allocated once. */
public final class MessageRequest {
    private static final int CAPACITY = 430;

    private final byte[] body = new byte[CAPACITY];

    byte[] body() {
        return body;
    }

    /** Encodes {@code {"title":...,"body":...}} into {@link #body()} and returns its length, or -1 if it does not fit. */
    int encode(char[] title, int titleLength, char[] body, int bodyLength) {
        int position = 0;
        position = appendAscii(position, "{\"title\":\"");
        position = appendJson(position, title, titleLength);
        position = appendAscii(position, "\",\"body\":\"");
        position = appendJson(position, body, bodyLength);
        position = appendAscii(position, "\"}");
        return position < 0 ? -1 : position;
    }

    private int appendJson(int position, char[] value, int length) {
        for (int index = 0; index < length; index++) {
            char character = value[index];
            if (character == '"' || character == '\\') {
                position = appendByte(position, '\\');
            } else if (character == '\n') {
                position = appendAscii(position, "\\n");
                continue;
            } else if (character == '\r') {
                position = appendAscii(position, "\\r");
                continue;
            }
            position = appendByte(position, character);
        }
        return position;
    }

    private int appendAscii(int position, String value) {
        for (int index = 0; index < value.length(); index++) {
            position = appendByte(position, value.charAt(index));
        }
        return position;
    }

    private int appendByte(int position, int value) {
        if (position < 0 || position >= body.length) {
            return -1;
        }
        body[position] = (byte) value;
        return position + 1;
    }
}
