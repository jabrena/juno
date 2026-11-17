package io.github.jabrena.juno.jdkx.net.ssl;

/**
 * The board-side {@code javax.net.ssl.KeyManager}, only as the type of {@link SSLContext#init}'s first parameter:
 * client certificates are not supported, so a program passes {@code null}.
 */
public interface KeyManager {
}
