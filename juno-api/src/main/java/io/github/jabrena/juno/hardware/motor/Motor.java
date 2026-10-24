package io.github.jabrena.juno.hardware.motor;

/**
 * A basic motor that can be driven by power. Implementations are supplied by a motor controller or
 * board facade; the interface itself contains only primitive operations, so a motor handle can be
 * used without allocating in the board program.
 *
 * <p>Power is expressed as a magnitude from {@code 0} to {@code 100} percent of the controller's
 * available power. Implementations clamp values outside that range; direction is selected with
 * {@link #forward()} or {@link #backward()}.
 */
public interface Motor {

    /** Sets the drive-power magnitude used by {@link #forward()} and {@link #backward()}. */
    void setPower(int percent);

    /** Runs the motor forward at its configured power. */
    void forward();

    /** Runs the motor backward at its configured power. */
    void backward();

    /** Stops driving and lets the motor coast. */
    void coast();

    /** Actively brakes the motor. */
    void brake();

    /** Stops the motor using the controller's normal stop behaviour. */
    void stop();
}
