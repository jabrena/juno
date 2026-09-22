package io.github.jabrena.juno.api.io.net.email;

/**
 * A minimal POP3S client recognized as a compiler intrinsic by Juno, backed by Arduino's {@code
 * WiFiSSLClient} — the same implicit-TLS mechanism as {@link
 * io.github.jabrena.juno.api.io.net.http.HttpsClient}, needing no extra library. Requires {@code
 * @Board(ArduinoUnoR4WiFi.class)} and an active {@link io.github.jabrena.juno.api.io.net.Wifi#begin}
 * connection.
 *
 * <p>{@code host}, {@code username}, and {@code password} must be compile-time strings (a
 * literal, or {@code System.getenv("NAME")} of a literal name). Responses use caller-owned
 * buffers; there is no heap allocation.
 */
public final class Pop3Client {
    /** A reasonable default size for a caller's headers buffer, in bytes. */
    public static final int DEFAULT_HEADERS_BUFFER_SIZE = 256;
    /** A reasonable default size for a caller's body buffer, in bytes. */
    public static final int DEFAULT_BODY_BUFFER_SIZE = 512;

    private Pop3Client() {
    }

    /**
     * Returns the mailbox's message count ({@code STAT}), or a negative code identifying the
     * failing step: connect ({@code -1}), greeting ({@code -2}), {@code USER} ({@code -3}),
     * {@code PASS} ({@code -4}), or {@code STAT} ({@code -5}).
     */
    public static native int messageCount(String host, int port, String username, String password);

    /**
     * Retrieves the newest message ({@code RETR} on the highest message number from {@code
     * STAT}), writing {@code "From: ...\n"} and {@code "Subject: ...\n"} (whichever are present,
     * each bounded to {@code headersBuffer}) into {@code headersBuffer} and the plain-text body
     * (dot-unstuffed, bounded to {@code bodyBuffer}) into {@code bodyBuffer}. {@code status[0]} is
     * set to the number of bytes written to {@code headersBuffer}. Returns the number of bytes
     * written to {@code bodyBuffer} on success, or a negative code identifying the failing step:
     * connect ({@code -1}), greeting ({@code -2}), {@code USER} ({@code -3}), {@code PASS}
     * ({@code -4}), {@code STAT} ({@code -5}), an empty mailbox ({@code -6}), {@code RETR}
     * ({@code -7}), or a connection drop while streaming the message ({@code -8}).
     */
    public static native int readLatest(String host, int port, String username, String password,
            byte[] headersBuffer, int headersBufferLength,
            byte[] bodyBuffer, int bodyBufferLength,
            int[] status);

    /**
     * Reads message {@code messageNumber}'s {@code Subject} header only, via {@code TOP
     * messageNumber 0} (headers, no body) — cheap enough to call once per message when listing a
     * mailbox, unlike {@link #readLatest}. {@code messageNumber} is 1-based POP3 numbering ({@code
     * 1} is the oldest message still in the mailbox). Writes the subject's value (bounded to
     * {@code subjectBuffer}, no trailing newline) into {@code subjectBuffer}. Returns the number
     * of bytes written on success (which is {@code 0} for a message with no {@code Subject}
     * header), or a negative code identifying the failing step: connect ({@code -1}), greeting
     * ({@code -2}), {@code USER} ({@code -3}), {@code PASS} ({@code -4}), {@code TOP} ({@code
     * -5}), or a connection drop while streaming the headers ({@code -6}).
     */
    public static native int readSubject(String host, int port, String username, String password,
            int messageNumber, byte[] subjectBuffer, int subjectBufferLength);
}
