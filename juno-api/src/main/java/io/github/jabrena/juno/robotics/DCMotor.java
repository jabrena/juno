package io.github.jabrena.juno.robotics;

/**
 * A DC motor driven by a power level. The power is a percentage from
 * {@code 0} to {@code 100} of the controller's available power; direction comes from {@link #forward()} and
 * {@link #backward()}.
 */
public interface DCMotor extends BaseMotor {

    /** Sets the power applied to the motor, {@code 0..100}; out-of-range values are clamped. */
    void setPower(int power);

    /** Returns the current power setting, {@code 0..100}. */
    int getPower();
}
