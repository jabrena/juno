package io.github.jabrena.juno.jdk.security;

/** The board-side {@code java.security.NoSuchAlgorithmException}. */
public class NoSuchAlgorithmException extends GeneralSecurityException {
    private static final long serialVersionUID = 1L;

    /** An exception with {@code message}. */
    public NoSuchAlgorithmException(String message) {
        super(message);
    }
}
