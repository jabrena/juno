/**
 * Basic SMTP send / POP3S read built on {@link io.github.jabrena.juno.api.io.net.Wifi}'s
 * connection: {@link io.github.jabrena.juno.api.io.net.email.Smtp} to send a plain-text message to
 * one recipient, and {@link io.github.jabrena.juno.api.io.net.email.Pop3Client} to report the
 * mailbox message count and read the newest message's {@code From}/{@code Subject} and a bounded
 * plain-text body into caller-owned buffers.
 *
 * <p>{@code Smtp} upgrades a plaintext connection to TLS with {@code STARTTLS} on port 587 (the
 * common mail-submission port), which the UNO R4 WiFi's native {@code WiFiSSLClient} cannot do —
 * it only ever negotiates TLS from the first byte. This needs the third-party {@code
 * ESP_SSLClient} library ({@code arduino-cli lib install ESP_SSLClient}), the same manual-install
 * pattern as {@link io.github.jabrena.juno.api.io.hid.Mouse}. {@code Pop3Client} instead uses
 * implicit TLS on port 995 (POP3S), so it reuses {@code WiFiSSLClient} exactly like {@link
 * io.github.jabrena.juno.api.io.net.http.HttpsClient} and needs no extra library.
 *
 * <p>Every host, username, password, and message field here must be a compile-time constant — a
 * string literal, or a direct {@code System.getenv("NAME")} call with a literal name — for the
 * same reason as {@link io.github.jabrena.juno.api.io.net.http}: Juno has no heap to build a
 * runtime {@code String} from.
 *
 * <p>Scope, deliberately: one recipient, plain text only, no attachments, HTML, multipart MIME,
 * folders, or OAuth2. {@code ESP_SSLClient} on this board validates the server host name but not
 * its certificate chain (no CA bundle available to the software TLS stack), unlike {@code
 * HttpsClient}/{@code Pop3Client}'s use of the WiFi module's own firmware-backed validation — a
 * deliberate tradeoff to fit STARTTLS in the UNO R4 WiFi's 32KB of RAM.
 */
package io.github.jabrena.juno.api.io.net.email;
