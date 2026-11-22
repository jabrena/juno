package io.github.jabrena.juno.javax.net.ssl;

import io.github.jabrena.juno.jdk.security.KeyStore;

/** Server identity subset of the JDK key manager factory. */
public final class KeyManagerFactory {
    private KeyManager[] managers = new KeyManager[0];

    private KeyManagerFactory() {
    }

    /** {@code "SunX509"}, the JDK's default name; the board accepts any. */
    public static String getDefaultAlgorithm() {
        return "SunX509";
    }

    /** A factory for the board's single server-identity key manager. */
    public static KeyManagerFactory getInstance(String algorithm) {
        return new KeyManagerFactory();
    }

    /** Takes the server key and certificate from {@code keyStore}; the in-memory store needs no password. */
    public void init(KeyStore keyStore, char[] password) {
        byte @org.jspecify.annotations.Nullable [] key = keyStore.serverPrivateKey();
        String certificate = keyStore.serverCertificate();
        if (key == null || certificate == null) throw new IllegalArgumentException("Server identity is missing");
        managers = new KeyManager[]{new PemKeyManager(certificate, key, keyStore.serverPrivateKeyLength())};
    }

    /** The key manager holding the identity from {@link #init(KeyStore, char[])}. */
    public KeyManager[] getKeyManagers() {
        return managers;
    }
}
