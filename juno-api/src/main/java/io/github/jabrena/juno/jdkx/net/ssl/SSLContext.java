package io.github.jabrena.juno.jdkx.net.ssl;

import io.github.jabrena.juno.jdk.security.KeyManagementException;
import io.github.jabrena.juno.jdk.security.NoSuchAlgorithmException;
import io.github.jabrena.juno.jdk.security.SecureRandom;
import org.jspecify.annotations.Nullable;

/**
 * The board-side {@code javax.net.ssl.SSLContext}: the trust a {@code java.net.http} client checks servers against,
 * given with {@code HttpClient.newBuilder().sslContext(context)}. It carries only trust anchors: the board's TLS
 * client does the handshake, so the protocol name is kept but not enforced, and client certificates are not
 * supported. Trust anchors take effect on the boards that can change them at runtime, the ESP32 boards and the
 * Raspberry Pi Pico 2 W; elsewhere a request through a client with an {@code SSLContext} fails with an
 * {@code IOException}.
 */
public final class SSLContext {
    private static @Nullable SSLContext defaultContext;

    private final String protocol;
    private @Nullable String pem;
    private boolean initialized;

    private SSLContext(String protocol) {
        this.protocol = protocol;
    }

    /**
     * An uninitialised context for {@code protocol}, such as {@code "TLS"}.
     *
     * @throws NoSuchAlgorithmException unless {@code protocol} names TLS
     */
    public static SSLContext getInstance(String protocol) throws NoSuchAlgorithmException {
        if (!protocol.equals("TLS") && !protocol.equals("TLSv1.2") && !protocol.equals("TLSv1.3")
                && !protocol.equals("Default")) {
            throw new NoSuchAlgorithmException(protocol + " SSLContext not available");
        }
        return new SSLContext(protocol);
    }

    /** The context that trusts the board's own roots. */
    public static SSLContext getDefault() throws NoSuchAlgorithmException {
        return boardDefault();
    }

    /** {@link #getDefault()} without its checked exception. Board-side only. */
    public static SSLContext boardDefault() {
        SSLContext context = defaultContext;
        if (context == null) {
            context = new SSLContext("Default");
            context.initialized = true;
            defaultContext = context;
        }
        return context;
    }

    /** The protocol the context was created for. */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Trusts the certificates of the first trust manager in {@code trustManagers}, or the board's own roots when there
     * is none. {@code random} is not used.
     *
     * @throws KeyManagementException when {@code keyManagers} asks for a client certificate
     */
    public void init(KeyManager @Nullable [] keyManagers, TrustManager @Nullable [] trustManagers,
            @Nullable SecureRandom random) throws KeyManagementException {
        if (keyManagers != null && keyManagers.length > 0) {
            throw new KeyManagementException("Client certificates are not supported on the board");
        }
        pem = null;
        if (trustManagers != null) {
            for (TrustManager manager : trustManagers) {
                if (manager instanceof PemTrustManager pemManager) {
                    pem = pemManager.pem();
                    break;
                }
            }
        }
        initialized = true;
    }

    /**
     * The trusted certificates as one PEM chain, or {@code null} for the board's own roots. Board-side only.
     *
     * @throws IllegalStateException when the context was not initialised
     */
    public @Nullable String trustedCertificates() {
        if (!initialized) {
            throw new IllegalStateException("SSLContext is not initialized");
        }
        return pem;
    }
}
