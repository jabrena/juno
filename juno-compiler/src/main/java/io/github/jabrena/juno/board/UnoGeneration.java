package io.github.jabrena.juno.board;

/**
 * The UNO generation a {@link Board} belongs to: the answer to {@code io.github.jabrena.juno.api.hardware.BoardModel}'s
 * {@code isR3()}/{@code isR4()}/{@code isQ()}. It is board data, so the backend folds those queries to constants by
 * comparing values, never by asking which board or core it is generating for.
 */
public enum UnoGeneration {
    /** The R3-format boards: the classic UNO R3 (ATmega328P) and the UNO WiFi Rev2 (ATmega4809). */
    R3,
    /** The UNO R4 boards. */
    R4,
    /** The UNO Q. */
    Q
}
