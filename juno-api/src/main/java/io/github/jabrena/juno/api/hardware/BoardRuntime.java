package io.github.jabrena.juno.api.hardware;

/**
 * Which board the program was compiled for, recognized as a compiler intrinsic by Juno. A program asks the question
 * without naming a board, so the same source runs on every board and each one answers for itself.
 */
public final class BoardRuntime {
    private BoardRuntime() {
    }

    /** Whether the program runs on the UNO Q, the board with the most memory and the 13-column LED matrix. */
    public static native boolean isQ();

    /** Whether the program runs on the UNO R4 WiFi, with its Renesas core and 12-column LED matrix. */
    public static native boolean isR4();

    /** Whether the program runs on an R3-format board, the UNO WiFi Rev2 with its MegaAVR core. */
    public static native boolean isR3();
}
