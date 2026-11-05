package io.github.jabrena.juno.usecases.mailbox.server.commons;

/** Publishes the one-way result of the background SD/TLS bootstrap to the TFT task. */
public final class MailboxStartupState {
    public static final int STARTING = 0;
    public static final int READY = 1;
    public static final int FAILED = 2;

    private volatile int status = STARTING;

    public int status() {
        return status;
    }

    public void ready() {
        status = READY;
    }

    public void failed() {
        status = FAILED;
    }
}
