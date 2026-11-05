package io.github.jabrena.juno.robotics;

/** The minimal contract for a gyroscope. */
public interface Gyroscope {

    /**
     * Returns the heading in whole degrees, from 0 up to 359, growing clockwise since the last {@link #reset()}.
     */
    int getAngle();

    /**
     * Returns the angular velocity in degrees per second, clockwise positive: the direction {@link #getAngle()} grows.
     * The offset from {@link #recalibrateOffset()} is already removed.
     */
    float getAngularVelocity();

    /** Measures and stores the offset, or bias, that {@link #getAngularVelocity()} subtracts. */
    void recalibrateOffset();

    /** Resets the accumulated {@link #getAngle()} to zero. */
    void reset();
}
