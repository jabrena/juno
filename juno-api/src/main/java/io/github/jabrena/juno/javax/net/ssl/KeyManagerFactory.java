package io.github.jabrena.juno.javax.net.ssl;

import io.github.jabrena.juno.jdk.security.KeyStore;

/** Server identity subset of the JDK key manager factory. */
public final class KeyManagerFactory {
    private KeyManager[] managers = new KeyManager[0];

    private KeyManagerFactory() {
    }

    public static String getDefaultAlgorithm() {
        return "SunX509";
    }

    public static KeyManagerFactory getInstance(String algorithm) {
        return new KeyManagerFactory();
    }

    public void init(KeyStore keyStore, char[] password) {
        byte @org.jspecify.annotations.Nullable [] key = keyStore.serverPrivateKey();
        String certificate = keyStore.serverCertificate();
        if (key == null || certificate == null) throw new IllegalArgumentException("Server identity is missing");
        managers = new KeyManager[]{new PemKeyManager(certificate, key, keyStore.serverPrivateKeyLength())};
    }

    public KeyManager[] getKeyManagers() {
        return managers;
    }
}
