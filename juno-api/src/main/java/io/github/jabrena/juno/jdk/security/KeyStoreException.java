package io.github.jabrena.juno.jdk.security;

/** The board-side {@code java.security.KeyStoreException}. */
public class KeyStoreException extends GeneralSecurityException {
    private static final long serialVersionUID = 1L;

    /** An exception with {@code message}. */
    public KeyStoreException(String message) {
        super(message);
    }
}
