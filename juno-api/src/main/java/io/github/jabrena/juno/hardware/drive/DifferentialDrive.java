package io.github.jabrena.juno.hardware.drive;

import io.github.jabrena.juno.hardware.motor.Motor;

/**
 * Coordinates two independently controlled motors as a differential drive. The drive only knows the
 * board-independent {@link Motor} contract, so a motor may be a PWM motor, a remote RCX motor, or a
 * regulated motor with native or external encoder feedback.
 *
 * <p>Create one instance during setup and reuse it in the control loop. Commands use signed power
 * values from {@code -100} to {@code 100}; positive values run forward and negative values run
 * backward. Values outside that range are clamped.
 */
public final class DifferentialDrive {
    /** The lowest signed power accepted by drive commands. */
    public static final int MIN_POWER = -100;

    /** The highest signed power accepted by drive commands. */
    public static final int MAX_POWER = 100;

    private final Motor leftMotor;
    private final Motor rightMotor;

    /** Creates a drive with the supplied left and right motors. */
    public DifferentialDrive(Motor leftMotor, Motor rightMotor) {
        this.leftMotor = leftMotor;
        this.rightMotor = rightMotor;
    }

    /** Drives the two sides independently with signed power values. */
    public void tank(int leftPower, int rightPower) {
        command(leftMotor, clamp(leftPower));
        command(rightMotor, clamp(rightPower));
    }

    /** Drives using forward power and a turn correction. Positive turn power speeds up the left side. */
    public void drive(int forwardPower, int turnPower) {
        int forward = clamp(forwardPower);
        int turn = clamp(turnPower);
        tank(forward + turn, forward - turn);
    }

    /** Drives both sides forward at the supplied power magnitude. */
    public void forward(int power) {
        int magnitude = clampMagnitude(power);
        tank(magnitude, magnitude);
    }

    /** Drives both sides backward at the supplied power magnitude. */
    public void backward(int power) {
        int magnitude = clampMagnitude(power);
        tank(-magnitude, -magnitude);
    }

    /** Stops both motors using their normal stop behavior. */
    public void stop() {
        leftMotor.stop();
        rightMotor.stop();
    }

    /** Stops both motors and lets them coast. */
    public void coast() {
        leftMotor.coast();
        rightMotor.coast();
    }

    /** Stops both motors with active braking. */
    public void brake() {
        leftMotor.brake();
        rightMotor.brake();
    }

    private static void command(Motor motor, int power) {
        if (power == 0) {
            motor.stop();
            return;
        }
        int magnitude = power < 0 ? -power : power;
        motor.setPower(magnitude);
        if (power > 0) {
            motor.forward();
        } else if (power < 0) {
            motor.backward();
        } else {
            motor.stop();
        }
    }

    private static int clampMagnitude(int power) {
        return clamp(power < 0 ? -power : power);
    }

    private static int clamp(int power) {
        return Math.max(MIN_POWER, Math.min(MAX_POWER, power));
    }
}
