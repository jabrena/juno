package io.github.jabrena.juno.javax.net.ssl;

/**
 * Board-side server identity passed through the JDK KeyManager interface. {@link SSLContext} reads it through the
 * static accessors, so a client-only program, which never creates one, still compiles.
 */
record PemKeyManager(String certificate, byte[] privateKey, int privateKeyLength) implements KeyManager {
    static String certificateOf(PemKeyManager manager) {
        return manager.certificate;
    }

    static byte[] privateKeyOf(PemKeyManager manager) {
        return manager.privateKey;
    }

    static int privateKeyLengthOf(PemKeyManager manager) {
        return manager.privateKeyLength;
    }
}
