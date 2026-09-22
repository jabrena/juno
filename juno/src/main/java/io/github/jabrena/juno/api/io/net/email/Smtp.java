package io.github.jabrena.juno.api.io.net.email;

/**
 * A minimal SMTP client recognized as a compiler intrinsic by Juno, backed by a plaintext {@code
 * WiFiClient} upgraded to TLS mid-connection with {@code STARTTLS} (the third-party {@code
 * ESP_SSLClient} library — see this package's Javadoc for why). Requires {@code
 * @Board(ArduinoUnoR4WiFi.class)} and an active {@link io.github.jabrena.juno.api.io.net.Wifi#begin}
 * connection.
 *
 * <p>{@code host}, {@code username}, {@code password}, {@code from}, {@code to}, {@code subject},
 * and {@code body} must all be compile-time strings (a literal, or {@code System.getenv("NAME")}
 * of a literal name). Authentication is {@code AUTH LOGIN} (base64 username/password), the
 * baseline every mainstream SMTP submission server accepts.
 */
public final class Smtp {
    private Smtp() {
    }

    /**
     * Sends a plain-text message to one recipient over {@code STARTTLS}. Returns {@code 0} on
     * success, or a negative code identifying the failing step: connect ({@code -1}), greeting
     * ({@code -2}), {@code EHLO} ({@code -3}), {@code AUTH LOGIN} ({@code -4}), authentication
     * ({@code -5}), {@code MAIL FROM} ({@code -6}), {@code RCPT TO} ({@code -7}), {@code DATA}
     * ({@code -8}), the message body ({@code -9}), a base64-encoding overflow ({@code -10}),
     * {@code STARTTLS} itself ({@code -11}), or the TLS upgrade ({@code -12}).
     */
    public static native int send(String host, int port, String username, String password,
            String from, String to, String subject, String body);
}
