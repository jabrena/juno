package io.github.jabrena.juno.usecases.mailbox.server;

import java.util.UUID;

/** A mailbox message and the {@code YYYYMMDDHHMMSS} folder holding its file on the SD card. */
public record MailboxMessage(UUID uuid, MailboxText title, MailboxText body, MailboxText createAt, long directoryDate) {
}
