package io.github.jabrena.juno.usecases.mailbox.server.services;

/**
 * A UTF-8 text field of a mailbox message: the first {@code length} bytes of {@code bytes}. A {@code byte[]} rather
 * than a {@code String}, so storing a message allocates nothing; the buffer is fixed-size because Juno arrays have
 * constant lengths.
 */
public record MailboxText(byte[] bytes, int length) {
}
