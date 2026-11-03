package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.robotics.DCMotor;

/**
 * One or more of an RCX brick's outputs, driven with direct commands over infrared: get them from
 * {@link RemoteRcx#getMotorA()}, {@link RemoteRcx#getMotorB()} and {@link RemoteRcx#getMotorC()}, and combine them with
 * {@link #and} so that several outputs start and stop in the same command, e.g. the two sides of a vehicle.
 *
 * <p>As a {@link DCMotor}, {@link #setPower} takes a percentage and is mapped onto the brick's {@link #MAX_LEVEL} levels.
 * Each handle keeps its own power and motion state, so a combined handle from {@link #and} does not see changes made
 * through the single-output handles.
 *
 * <p>Each call is one or more commands that wait for the brick's acknowledgement when the IR receiver is wired, so it
 * takes 100 ms or more; set a speed once and not in a tight loop.
 */
public final class RcxMotor implements DCMotor {
    /**
     * The highest power level an RCX output accepts: the Output Power command takes a level from {@code 0} (weakest) to
     * {@code 7} (full power), so the brick has eight levels, not a percentage. {@link #drive} and
     * {@link RcxBrick#setMotorPower} use this scale, and {@link #setPower} maps its percentage onto it.
     */
    public static final int MAX_LEVEL = 7;

    private static final int MAX_PERCENT = 100;

    private final RcxBrick brick;
    private final int outputs;
    private int power = MAX_PERCENT;
    private boolean moving;

    RcxMotor(RcxBrick brick, int outputs) {
        this.brick = brick;
        this.outputs = outputs;
    }

    /** These outputs and {@code other}'s, driven together in one command. Combine them once, at startup. */
    public RcxMotor and(RcxMotor other) {
        return new RcxMotor(brick, outputs | other.outputs);
    }

    /** Runs the outputs forwards, at the power level last set. */
    @Override
    public void forward() {
        brick.setMotorDirection(outputs, true);
        brick.motorOn(outputs);
        moving = true;
    }

    /** Runs the outputs backwards, at the power level last set. */
    @Override
    public void backward() {
        brick.setMotorDirection(outputs, false);
        brick.motorOn(outputs);
        moving = true;
    }

    /** Switches the outputs off with the motors braking. */
    @Override
    public void stop() {
        brick.motorOff(outputs);
        moving = false;
    }

    /** Switches the outputs off with the motors free to coast. */
    @Override
    public void flt() {
        brick.motorFloat(outputs);
        moving = false;
    }

    /**
     * Returns {@code true} while the outputs are commanded on, as the last {@link #drive}, {@link #forward} or
     * {@link #backward} left them.
     */
    @Override
    public boolean isMoving() {
        return moving;
    }

    /**
     * Sets the power, {@code 0..100} (clamped), mapped onto the {@code 0..}{@link #MAX_LEVEL} levels; it takes effect
     * while the outputs are on.
     */
    @Override
    public void setPower(int percent) {
        power = clamp(percent, MAX_PERCENT);
        brick.setMotorPower(outputs, (power * MAX_LEVEL + MAX_PERCENT / 2) / MAX_PERCENT);
    }

    @Override
    public int getPower() {
        return power;
    }

    /**
     * Runs the outputs at {@code speed}, {@code -MAX_LEVEL..MAX_LEVEL}: the sign is the direction, the size the power
     * level, and {@code 0} brakes them. That is up to three commands.
     */
    public void drive(int speed) {
        brick.drive(outputs, speed);
        moving = speed != 0;
        if (moving) {
            power = clamp(speed < 0 ? -speed : speed, MAX_LEVEL) * MAX_PERCENT / MAX_LEVEL;
        }
    }

    private static int clamp(int value, int max) {
        return value < 0 ? 0 : value > max ? max : value;
    }
}
