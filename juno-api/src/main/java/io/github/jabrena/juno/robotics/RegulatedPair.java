package io.github.jabrena.juno.robotics;

/**
 * Two regulated motors that start, stop and report as one unit, such as the two drive wheels of a vehicle. A
 * {@link #rotate(int, int, boolean)} is one coordinated movement, not two independent ones, so both sides begin on the
 * same control cycle.
 *
 * <p>Speeds and rotations are in encoder-shaft degrees per side, as in {@link RegulatedMotor}. Board-independent code
 * (such as differential-drive navigation) depends on this interface; the RCX implementation lives with the RCX link.
 */
public interface RegulatedPair {

    /** Sets the target speed, in degrees per second, used by subsequent movements of both sides. */
    void setSpeed(int degreesPerSecond);

    /** Sets the acceleration, in degrees per second squared, used by subsequent movements of both sides. */
    void setAcceleration(int degreesPerSecondSquared);

    /**
     * Rotates the left side by {@code leftDegrees} and the right side by {@code rightDegrees}, relative to their current
     * positions, starting both in one coordinated movement. Waits for completion unless {@code immediateReturn} is set.
     */
    void rotate(int leftDegrees, int rightDegrees, boolean immediateReturn);

    /** Stops the movement on both sides. */
    void stop();

    /** Returns whether a coordinated movement is in progress. */
    boolean isMoving();

    /** Returns whether the controller has detected that a side cannot complete its movement. */
    boolean isStalled();

    /** Waits until the current movement completes or reports an error state. */
    void waitComplete();
}
