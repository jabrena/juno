package io.github.jabrena.juno.jdk.security.cert;

import io.github.jabrena.juno.jdk.security.GeneralSecurityException;

/** The board-side {@code java.security.cert.CertificateException}. */
public class CertificateException extends GeneralSecurityException {
    private static final long serialVersionUID = 1L;

    /** An exception with {@code message}. */
    public CertificateException(String message) {
        super(message);
    }
}
