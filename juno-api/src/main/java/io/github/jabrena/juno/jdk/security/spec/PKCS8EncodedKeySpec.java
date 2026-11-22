package io.github.jabrena.juno.jdk.security.spec;

/** An EC private key in PKCS#8 DER form. */
public final class PKCS8EncodedKeySpec implements KeySpec {
    private final byte[] encoded;
    private final int length;

    /** A key specification for the DER bytes in {@code encodedKey}. */
    public PKCS8EncodedKeySpec(byte[] encodedKey) {
        encoded = encodedKey;
        length = encodedKey.length;
    }

    /** The DER bytes. */
    public byte[] getEncoded() {
        return encoded;
    }

    /** The length of the DER encoding, read from its outer length field. */
    public int encodedLength() {
        return length;
    }
}
