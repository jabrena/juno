package io.github.jabrena.juno.hardware;

/**
 * Which board the program was compiled for. {@link Arduino#getBoardModel()} is the board's own answer, which the compiler
 * folds to a constant for the target board; a unit test passes {@code mock(BoardModel.class)} instead. A program
 * asks the question without naming a board, so the same source runs on every board and each one answers for itself.
 */
public interface BoardModel {

    /** Whether the program runs on the UNO Q, the board with the most memory and the 13-column LED matrix. */
    boolean isQ();

    /** Whether the program runs on the UNO R4 WiFi, with its Renesas core and 12-column LED matrix. */
    boolean isR4();

    /** Whether the program runs on an R3-format board, the UNO WiFi Rev2 with its MegaAVR core. */
    boolean isR3();
}
