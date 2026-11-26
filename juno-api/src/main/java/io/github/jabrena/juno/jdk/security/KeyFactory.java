package io.github.jabrena.juno.jdk.security;

import io.github.jabrena.juno.jdk.security.spec.KeySpec;
import io.github.jabrena.juno.jdk.security.spec.PKCS8EncodedKeySpec;

/** The EC PKCS#8 subset of the JDK key factory. */
public final class KeyFactory {
    private KeyFactory() {
    }

    /** A factory for {@code "EC"} keys, the only algorithm the board supports. */
    public static KeyFactory getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (!algorithm.equals("EC")) throw new NoSuchAlgorithmException(algorithm);
        return new KeyFactory();
    }

    /** A private key from a {@link PKCS8EncodedKeySpec}; the board's TLS stack also accepts SEC1 DER in it. */
    public PrivateKey generatePrivate(KeySpec keySpec) {
        if (!(keySpec instanceof PKCS8EncodedKeySpec spec)) {
            throw new IllegalArgumentException("Expected a PKCS#8 private key");
        }
        return new EncodedPrivateKey(spec.getEncoded(), derLength(spec.getEncoded()));
    }

    private static int derLength(byte[] encoded) {
        if (encoded.length < 2 || (encoded[0] & 255) != 0x30) return encoded.length;
        int length = encoded[1] & 255;
        if ((length & 128) == 0) return length + 2;
        int bytes = length & 127;
        int value = 0;
        for (int index = 0; index < bytes && index + 2 < encoded.length; index++) {
            value = (value << 8) | (encoded[index + 2] & 255);
        }
        return value + bytes + 2;
    }

    /** The length of {@code key}'s DER encoding, which may be shorter than its encoded array. */
    public static int encodedLength(PrivateKey key) {
        return key instanceof EncodedPrivateKey encoded ? encoded.length : key.getEncoded().length;
    }

    @SuppressWarnings("ArrayRecordComponent")
    private record EncodedPrivateKey(byte[] encoded, int length) implements PrivateKey {
        @Override
        public byte[] getEncoded() {
            return encoded;
        }
    }
}
