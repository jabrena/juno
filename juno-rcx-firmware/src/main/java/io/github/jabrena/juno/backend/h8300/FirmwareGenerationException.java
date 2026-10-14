package io.github.jabrena.juno.backend.h8300;

/** A user-facing error produced while assembling or generating the RCX firmware. */
public final class FirmwareGenerationException extends RuntimeException {
    public FirmwareGenerationException(String message) {
        super(message);
    }
}
