package io.github.jabrena.juno.usecases.mailbox.client.services;

import io.github.jabrena.juno.hardware.net.http.JsonWriter;

/** The JSON body of a new mailbox message. */
public final class MessageRequest {
    /** The longest body, in bytes, the mailbox board accepts. */
    private static final int CAPACITY = 430;

    private final JsonWriter json = new JsonWriter(new byte[CAPACITY]);

    /** Encodes {@code {"title":...,"body":...}}, or returns {@code null} when it is longer than the board accepts. */
    String encode(char[] title, int titleLength, char[] body, int bodyLength) {
        json.reset().beginObject().name("title").value(title, titleLength).name("body").value(body, bodyLength)
                .endObject();
        return json.overflowed() ? null : json.toString();
    }
}
