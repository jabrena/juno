package io.github.jabrena.juno.jdk.security;

/** Encoded board key. */
public interface Key {
    /** The key in its DER encoding. */
    byte[] getEncoded();
}
