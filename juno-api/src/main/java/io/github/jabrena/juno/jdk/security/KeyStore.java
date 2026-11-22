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
    private @Nullable Key privateKey;
    private int privateKeyLength;
    private @Nullable Certificate identityCertificate;
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
        privateKey = null;
        privateKeyLength = 0;
        identityCertificate = null;
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

    /** Records one server identity in the in-memory store. */
    public void setKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        requireLoaded();
        if (chain.length == 0) throw new KeyStoreException("A certificate chain is required");
        PrivateKey privateKeyValue = (PrivateKey) key;
        privateKey = privateKeyValue;
        privateKeyLength = KeyFactory.encodedLength(privateKeyValue);
        identityCertificate = chain[0];
        size++;
    }

    /** Board transport access to the key encoded in PKCS#8 DER. */
    public byte @Nullable [] serverPrivateKey() {
        Key key = privateKey;
        return key == null ? null : key.getEncoded();
    }

    public int serverPrivateKeyLength() {
        return privateKeyLength;
    }

    /** Board transport access to the X.509 certificate PEM. */
    public @Nullable String serverCertificate() {
        Certificate certificate = identityCertificate;
        return certificate == null ? null : certificate.pem();
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
