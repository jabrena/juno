package io.github.jabrena.juno.usecases.mailbox.server;

/**
 * The small state hand-off from the HTTPS task to the TFT task. The server publishes a new message count; the main
 * task consumes the revision and is the only task that draws the display.
 */
final class MailboxUiState {
    private volatile int messageCount;
    private volatile int revision;

    int messageCount() {
        return messageCount;
    }

    int revision() {
        return revision;
    }

    void publishMessageCount(int count) {
        messageCount = count;
        revision = revision + 1;
    }
}
