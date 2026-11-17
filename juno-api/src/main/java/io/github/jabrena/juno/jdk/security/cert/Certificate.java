package io.github.jabrena.juno.jdk.security.cert;

/**
 * The board-side {@code java.security.cert.Certificate}: an X.509 certificate as the PEM text it was read from. The
 * board's TLS client parses it when a request uses it, so it is kept as text, not decoded here.
 */
public final class Certificate {
    private final String pem;

    Certificate(String pem) {
        this.pem = pem;
    }

    /** {@code "X.509"}. */
    public String getType() {
        return "X.509";
    }

    /** The PEM text, every certificate it holds included. Board-side only: the JDK class has no such method. */
    public String pem() {
        return pem;
    }
}
