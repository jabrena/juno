package io.github.jabrena.juno.dm;

/** Thrown when a JEV model or inference request violates its declared shape. */
public final class JevModelException extends IllegalArgumentException {
    public JevModelException(String message) {
        super(message);
    }
}
