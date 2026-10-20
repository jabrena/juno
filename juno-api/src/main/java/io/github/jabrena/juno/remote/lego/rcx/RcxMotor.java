package io.github.jabrena.juno.remote.lego.rcx;

/**
 * One or more of an RCX brick's outputs, driven with direct commands over infrared: get them from
 * {@link RemoteRcx#getMotorA()}, {@link RemoteRcx#getMotorB()} and {@link RemoteRcx#getMotorC()}, and combine them with
 * {@link #and} so that several outputs start and stop in the same command, e.g. the two sides of a vehicle.
 *
 * <p>Each call is one or more commands that wait for the brick's acknowledgement when the IR receiver is wired, so it
 * takes 100 ms or more; set a speed once and not in a tight loop.
 */
public final class RcxMotor {
    /** The highest power level of {@link #setPower} and {@link #drive}. */
    public static final int MAX_POWER = 7;

    private final RcxBrick brick;
    private final int outputs;

    RcxMotor(RcxBrick brick, int outputs) {
        this.brick = brick;
        this.outputs = outputs;
    }

    /** These outputs and {@code other}'s, driven together in one command. Combine them once, at startup. */
    public RcxMotor and(RcxMotor other) {
        return new RcxMotor(brick, outputs | other.outputs);
    }

    /** Runs the outputs forwards, at the power level last set. */
    public void forward() {
        brick.setMotorDirection(outputs, true);
        brick.motorOn(outputs);
    }

    /** Runs the outputs backwards, at the power level last set. */
    public void backward() {
        brick.setMotorDirection(outputs, false);
        brick.motorOn(outputs);
    }

    /** Switches the outputs off with the motors braking. */
    public void stop() {
        brick.motorOff(outputs);
    }

    /** Switches the outputs off with the motors free to coast. */
    public void flt() {
        brick.motorFloat(outputs);
    }

    /** Sets the power level, {@code 0..MAX_POWER} (clamped); it takes effect while the outputs are on. */
    public void setPower(int level) {
        brick.setMotorPower(outputs, level);
    }

    /**
     * Runs the outputs at {@code speed}, {@code -MAX_POWER..MAX_POWER}: the sign is the direction, the size the power
     * level, and {@code 0} brakes them. That is up to three commands.
     */
    public void drive(int speed) {
        brick.drive(outputs, speed);
    }
}
