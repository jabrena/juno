package io.github.jabrena.juno.board;

/**
 * The UNO generation a {@link Board} belongs to: the answer to {@code io.github.jabrena.juno.api.BoardRuntime}'s
 * {@code isR3()}/{@code isR4()}/{@code isQ()}. It is board data, so the backend folds those queries to constants by
 * comparing values, never by asking which board or core it is generating for.
 */
public enum UnoGeneration {
    /** R3-format boards, such as the UNO WiFi Rev2. */
    R3,
    /** The UNO R4 boards. */
    R4,
    /** The UNO Q. */
    Q
}
