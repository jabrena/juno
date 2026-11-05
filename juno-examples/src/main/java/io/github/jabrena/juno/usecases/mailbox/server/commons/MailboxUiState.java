package io.github.jabrena.juno.usecases.mailbox.server.commons;

/**
 * The small state hand-off from the HTTPS task to the TFT task. The server publishes a new message count; the main
 * task consumes the revision and is the only task that draws the display.
 */
public final class MailboxUiState {
    private volatile int messageCount;
    private volatile int revision;

    public int messageCount() {
        return messageCount;
    }

    public int revision() {
        return revision;
    }

    public void publishMessageCount(int count) {
        messageCount = count;
        revision = revision + 1;
    }
}
