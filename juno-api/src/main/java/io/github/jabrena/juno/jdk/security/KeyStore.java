package io.github.jabrena.juno.jdk.security;

import io.github.jabrena.juno.jdk.security.cert.Certificate;
import io.github.jabrena.juno.jdk.security.cert.CertificateException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * The board-side {@code java.security.KeyStore}: an in-memory store of trusted certificates for a
 * {@code java.net.http} client's {@code SSLContext}. Start it empty with {@code load(null, null)} and add each
 * certificate with {@link #setCertificateEntry}. A key store file cannot be read, and the store holds no private keys,
 * so client certificates are not supported. The certificates are kept as one PEM chain; the aliases only count.
 */
public final class KeyStore {
    private @Nullable String pem;
    private int size;
    private boolean loaded;

    private KeyStore() {
    }

    /** An empty store of any {@code type}; call {@code load(null, null)} before adding to it. */
    public static KeyStore getInstance(String type) throws KeyStoreException {
        return new KeyStore();
    }

    /** {@code "pkcs12"}, as in the JDK. */
    public static String getDefaultType() {
        return "pkcs12";
    }

    /**
     * Starts the store empty when {@code stream} is {@code null}.
     *
     * @throws IOException when {@code stream} is not {@code null}: a key store file cannot be read on the board
     */
    public void load(@Nullable InputStream stream, char @Nullable [] password)
            throws IOException, NoSuchAlgorithmException, CertificateException {
        if (stream != null) {
            throw new IOException("A key store file cannot be read on the board: load(null, null), then add certificates");
        }
        pem = null;
        size = 0;
        loaded = true;
    }

    /**
     * Adds {@code certificate} to the trusted certificates.
     *
     * @throws KeyStoreException when the store was not loaded first
     */
    public void setCertificateEntry(String alias, Certificate certificate) throws KeyStoreException {
        requireLoaded();
        String current = pem;
        pem = current == null ? certificate.pem() : current + "\n" + certificate.pem();
        size++;
    }

    /**
     * How many certificates the store holds.
     *
     * @throws KeyStoreException when the store was not loaded first
     */
    public int size() throws KeyStoreException {
        requireLoaded();
        return size;
    }

    /** Every certificate in the store, as one PEM chain, or {@code null} when it holds none. Board-side only. */
    public @Nullable String pem() {
        return pem;
    }

    private void requireLoaded() throws KeyStoreException {
        if (!loaded) {
            throw new KeyStoreException("Uninitialized keystore");
        }
    }
}
