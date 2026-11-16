package io.github.jabrena.juno.jdk.net.http;

import java.io.IOException;

/**
 * The board-side {@code java.net.http.HttpTimeoutException}: thrown by {@link HttpClient#send} when no response arrived
 * within the request's timeout. A program catches it as the JDK's class, or as any {@link IOException}.
 */
public class HttpTimeoutException extends IOException {
    private static final long serialVersionUID = 1L;

    /** An exception with {@code message}. */
    public HttpTimeoutException(String message) {
        super(message);
    }
}
