package io.github.jabrena.juno.lm;

/** A JLM1 file, dimension or text that the board engine would reject. */
public final class JlmFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JlmFormatException(String message) {
        super(message);
    }
}
