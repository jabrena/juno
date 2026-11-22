package io.github.jabrena.juno.jdk.security.spec;

/** An EC private key in PKCS#8 DER form. */
public final class PKCS8EncodedKeySpec implements KeySpec {
    private final byte[] encoded;
    private final int length;

    public PKCS8EncodedKeySpec(byte[] encodedKey) {
        encoded = encodedKey;
        length = encodedKey.length;
    }

    public byte[] getEncoded() {
        return encoded;
    }

    public int encodedLength() {
        return length;
    }
}
