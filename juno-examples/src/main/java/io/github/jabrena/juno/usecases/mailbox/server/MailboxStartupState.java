package io.github.jabrena.juno.usecases.mailbox.server;

/** Publishes the one-way result of the background SD/TLS bootstrap to the TFT task. */
final class MailboxStartupState {
    static final int STARTING = 0;
    static final int READY = 1;
    static final int FAILED = 2;

    private volatile int status = STARTING;

    int status() {
        return status;
    }

    void ready() {
        status = READY;
    }

    void failed() {
        status = FAILED;
    }
}
