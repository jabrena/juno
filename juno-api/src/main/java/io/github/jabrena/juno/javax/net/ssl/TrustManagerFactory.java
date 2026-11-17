package io.github.jabrena.juno.javax.net.ssl;

import io.github.jabrena.juno.jdk.security.KeyStore;
import io.github.jabrena.juno.jdk.security.KeyStoreException;
import io.github.jabrena.juno.jdk.security.NoSuchAlgorithmException;
import org.jspecify.annotations.Nullable;

/**
 * The board-side {@code javax.net.ssl.TrustManagerFactory}: turns a {@link KeyStore} of trusted certificates into
 * the {@link TrustManager} an {@link SSLContext} takes. Initialised with {@code null}, it trusts the board's own roots.
 */
public final class TrustManagerFactory {
    private final String algorithm;
    private @Nullable String pem;
    private boolean initialized;

    private TrustManagerFactory(String algorithm) {
        this.algorithm = algorithm;
    }

    /** {@code "PKIX"}, as in the JDK. */
    public static String getDefaultAlgorithm() {
        return "PKIX";
    }

    /** A factory for {@code algorithm}; every algorithm checks the chain the same way, with the board's TLS client. */
    public static TrustManagerFactory getInstance(String algorithm) throws NoSuchAlgorithmException {
        return new TrustManagerFactory(algorithm);
    }

    /** The algorithm the factory was asked for. */
    public String getAlgorithm() {
        return algorithm;
    }

    /** Trusts the certificates in {@code store}, or the board's own roots when it is {@code null}. */
    public void init(@Nullable KeyStore store) throws KeyStoreException {
        pem = store == null ? null : store.pem();
        initialized = true;
    }

    /**
     * The trust manager for the certificates {@link #init} was given.
     *
     * @throws IllegalStateException when the factory was not initialised
     */
    public TrustManager[] getTrustManagers() {
        if (!initialized) {
            throw new IllegalStateException("TrustManagerFactoryImpl is not initialized");
        }
        return new TrustManager[] {new PemTrustManager(pem)};
    }
}
