package io.github.jabrena.juno.board;

/**
 * The UNO generation a {@link Board} belongs to. It is board data, so the backend compares values, never asking which
 * board or core it is generating for.
 */
public enum UnoGeneration implements BoardFamily {
    /** The R3-format boards: the classic UNO R3 (ATmega328P). */
    R3,
    /** The UNO R4 boards. */
    R4
}
