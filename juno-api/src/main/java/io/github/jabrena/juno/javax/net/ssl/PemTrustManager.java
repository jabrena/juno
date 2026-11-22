package io.github.jabrena.juno.javax.net.ssl;

import org.jspecify.annotations.Nullable;

/** The one {@link TrustManager} the board makes: trusts the certificates of a PEM chain, or the board's own. */
final class PemTrustManager implements TrustManager {
    private final @Nullable String pem;

    PemTrustManager(@Nullable String pem) {
        this.pem = pem;
    }

    /** The trusted certificates as one PEM chain, or {@code null} for the board's own trust. */
    @Nullable String pem() {
        return pem;
    }

    static @Nullable String pemOf(PemTrustManager manager) {
        return manager.pem;
    }
}
