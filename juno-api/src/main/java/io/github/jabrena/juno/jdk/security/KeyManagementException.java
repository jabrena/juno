package io.github.jabrena.juno.jdk.security;

/** The board-side {@code java.security.KeyManagementException}. */
public class KeyManagementException extends GeneralSecurityException {
    private static final long serialVersionUID = 1L;

    /** An exception with {@code message}. */
    public KeyManagementException(String message) {
        super(message);
    }
}
