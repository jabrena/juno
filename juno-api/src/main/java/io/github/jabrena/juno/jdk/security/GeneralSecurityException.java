package io.github.jabrena.juno.jdk.security;

/** The board-side {@code java.security.GeneralSecurityException}. */
public class GeneralSecurityException extends Exception {
    private static final long serialVersionUID = 1L;

    /** An exception with {@code message}. */
    public GeneralSecurityException(String message) {
        super(message);
    }
}
