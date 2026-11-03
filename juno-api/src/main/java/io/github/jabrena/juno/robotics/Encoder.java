package io.github.jabrena.juno.robotics;

/**
 * Tachometer feedback for a motor or wheel, after leJOS's {@code Encoder}. Implementations may be integrated into a
 * motor controller or provided by a separate sensor, such as an RCX rotation sensor. The count is in reference-shaft
 * degrees, and the contract uses only primitive operations so polling allocates nothing.
 */
public interface Encoder {

    /** Returns the tachometer count in reference-shaft degrees. */
    int getTachoCount();

    /** Resets the tachometer count to zero. */
    void resetTachoCount();
}
