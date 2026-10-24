package io.github.jabrena.juno.hardware.motor;

/**
 * A motor with encoder feedback and closed-loop speed/position control.
 *
 * <p>Speeds and accelerations are expressed in degrees per second and degrees per second squared.
 * Tachometer positions and rotation arguments are expressed in motor-shaft degrees. A rotation
 * with {@code immediateReturn == false} waits until the requested motion completes; a rotation
 * with {@code immediateReturn == true} starts the motion and returns immediately.
 */
public interface RegulatedMotor extends Motor {

    /** Sets the target speed used by subsequent regulated movements. */
    void setSpeed(int degreesPerSecond);

    /** Sets the acceleration used by subsequent regulated movements. */
    void setAcceleration(int degreesPerSecondSquared);

    /** Returns the configured target speed in degrees per second. */
    int speed();

    /** Rotates by {@code degrees} relative to the current tachometer position. */
    void rotate(int degrees);

    /** Rotates by {@code degrees}, optionally returning before the motion completes. */
    void rotate(int degrees, boolean immediateReturn);

    /** Rotates to the absolute tachometer position {@code limitAngle}. */
    void rotateTo(int limitAngle);

    /** Rotates to {@code limitAngle}, optionally returning before the motion completes. */
    void rotateTo(int limitAngle, boolean immediateReturn);

    /** Returns the current encoder position in motor-shaft degrees. */
    int tachoCount();

    /** Resets the encoder position to zero. */
    void resetTachoCount();

    /** Returns whether the motor is currently executing a movement. */
    boolean isMoving();

    /** Returns whether the controller has detected that the motor cannot complete its movement. */
    boolean isStalled();

    /** Waits until the current regulated movement completes. */
    void waitComplete();
}
