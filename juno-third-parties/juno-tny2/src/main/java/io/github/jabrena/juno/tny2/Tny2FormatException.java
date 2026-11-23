package io.github.jabrena.juno.tny2;

/** A TNY2 file, dimension or text that the board engine would reject. */
public final class Tny2FormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public Tny2FormatException(String message) {
        super(message);
    }
}
