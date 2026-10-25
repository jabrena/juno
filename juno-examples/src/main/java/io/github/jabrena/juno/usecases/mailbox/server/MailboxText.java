package io.github.jabrena.juno.usecases.mailbox.server;

/**
 * A UTF-8 text field of a mailbox message: the first {@code length} bytes of {@code bytes}. A {@code byte[]} rather
 * than a {@code String}, since on the board a runtime {@code String} lives in a small rotating pool of 32-byte slots
 * and would be overwritten by later strings; the buffer is fixed-size because Juno arrays have constant lengths.
 */
public record MailboxText(byte[] bytes, int length) {
}
