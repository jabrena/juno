package io.github.jabrena.juno.jdk.security.cert;

import java.io.IOException;
import java.io.InputStream;

/**
 * The board-side {@code java.security.cert.CertificateFactory}, for {@code "X.509"} certificates in PEM form, such as
 * a CA certificate read from the SD card. Unlike the JDK, {@link #generateCertificate} keeps every certificate the
 * stream holds, so a PEM chain is trusted as a whole.
 */
public final class CertificateFactory {
    private static final String TYPE = "X.509";
    private static final String BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final int FIRST_CAPACITY = 256;

    private CertificateFactory() {
    }

    /**
     * A factory for {@code type}.
     *
     * @throws CertificateException unless {@code type} is {@code "X.509"}
     */
    public static CertificateFactory getInstance(String type) throws CertificateException {
        if (!type.equals(TYPE)) {
            throw new CertificateException(type + " not found");
        }
        return new CertificateFactory();
    }

    /** {@code "X.509"}. */
    public String getType() {
        return TYPE;
    }

    /**
     * Reads {@code stream} to its end as a PEM certificate.
     *
     * @throws CertificateException when it cannot be read or holds no {@code BEGIN CERTIFICATE} block
     */
    public Certificate generateCertificate(InputStream stream) throws CertificateException {
        String pem;
        try {
            pem = readAll(stream);
        } catch (IOException e) {
            throw new CertificateException("Could not read the certificate");
        }
        if (!contains(pem, BEGIN)) {
            throw new CertificateException("Could not parse certificate: no PEM certificate");
        }
        return new Certificate(pem);
    }

    /** Every byte left in {@code stream}, as a {@code String} sized to it. */
    private static String readAll(InputStream stream) throws IOException {
        int available = stream.available();
        int capacity = available > FIRST_CAPACITY ? available : FIRST_CAPACITY;
        StringBuilder text = new StringBuilder(capacity);
        int length = 0;
        int value = stream.read();
        while (value >= 0) {
            if (length == capacity) {
                // A StringBuilder drops what passes its capacity on the board, so a longer stream moves to a bigger one.
                capacity = capacity * 2;
                StringBuilder bigger = new StringBuilder(capacity);
                bigger.append(text.toString());
                text = bigger;
            }
            text.append((char) value);
            length++;
            value = stream.read();
        }
        return text.toString();
    }

    private static boolean contains(String text, String part) {
        for (int start = 0; start + part.length() <= text.length(); start++) {
            int i = 0;
            while (i < part.length() && text.charAt(start + i) == part.charAt(i)) {
                i++;
            }
            if (i == part.length()) {
                return true;
            }
        }
        return false;
    }
}
