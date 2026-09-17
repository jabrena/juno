package io.github.jabrena.juno;

/** A user-facing error produced while linking or compiling Java bytecode. */
public final class CompileException extends RuntimeException {
    public CompileException(String message) {
        super(message);
    }

    public CompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
