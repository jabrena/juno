package io.github.jabrena.juno.hardware.motor;

/**
 * Position feedback for a motor or wheel. Implementations may be integrated into a motor controller
 * or provided by a separate sensor, such as an RCX rotation sensor. Positions are expressed in
 * reference-shaft degrees and the contract uses only primitive operations so polling allocates nothing.
 */
public interface Encoder {

    /** Returns the current position in reference-shaft degrees. */
    int positionDegrees();

    /** Resets the current position to zero. */
    void reset();
}
