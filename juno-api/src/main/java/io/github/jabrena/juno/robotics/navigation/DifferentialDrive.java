package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.DCMotor;

/**
 * Coordinates two independently controlled motors as a differential drive. The drive only knows the
 * board-independent {@link DCMotor} contract, so a motor may be a Power Functions motor, a remote RCX motor, or a
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

    private final DCMotor leftMotor;
    private final DCMotor rightMotor;

    /** Creates a drive with the supplied left and right motors. */
    public DifferentialDrive(DCMotor leftMotor, DCMotor rightMotor) {
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

    /** Stops both motors with active braking, as {@link DCMotor#stop()} does. */
    public void stop() {
        leftMotor.stop();
        rightMotor.stop();
    }

    /** Removes power from both motors so they coast, as {@link DCMotor#flt()} does. */
    public void flt() {
        leftMotor.flt();
        rightMotor.flt();
    }

    private static void command(DCMotor motor, int power) {
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
