package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Restores the SD-backed message index without delaying HTTPS availability or TFT input. */
final class MailboxRestoreTask implements Runnable {
    private final MailboxModel model;
    private final MailboxUiState uiState;

    MailboxRestoreTask(MailboxModel model, MailboxUiState uiState) {
        this.model = model;
        this.uiState = uiState;
    }

    @Override
    public void run() {
        // Let the parent task enter HttpServer.accept() before the SD scan starts. A newly started task may otherwise
        // win the next scheduler slice and keep the Linux-side listener queued without an MCU-side TLS accept.
        Thread.yield();
        Delay.millis(100);
        Serial.println("Mailbox message restore");
        model.restore();
        uiState.publishMessageCount(model.messageCount());
        Serial.println("Mailbox restore complete");
    }
}
