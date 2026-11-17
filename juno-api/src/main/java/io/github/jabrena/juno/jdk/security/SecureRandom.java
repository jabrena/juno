package io.github.jabrena.juno.jdk.security;

/**
 * The board-side {@code java.security.SecureRandom}, only as the type of {@code SSLContext.init}'s last parameter,
 * which a program passes {@code null}: the board's TLS client brings its own randomness. It cannot be created, so a
 * program that does fails when it is linked rather than getting numbers that are not secure.
 */
public final class SecureRandom {
    private SecureRandom() {
    }
}
