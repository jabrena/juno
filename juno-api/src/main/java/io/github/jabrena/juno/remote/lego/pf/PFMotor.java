package io.github.jabrena.juno.remote.lego.pf;

import io.github.jabrena.juno.robotics.DCMotor;

/**
 * One output of a Power Functions receiver as a {@link DCMotor}: a motor, light or servo plugged into the red or the
 * blue port of a receiver on a channel. Create it once at startup with its port, as
 * {@link io.github.jabrena.juno.remote.lego.rcx.RcxMotor} is bound to its output, and it sends single-output commands
 * to that port only, so the other port is left alone.
 *
 * <p>The power percentage is mapped onto the protocol's seven speed steps: 100 percent is speed 7, and each step is
 * about 14 percent. The protocol does not say which motor is plugged in (XL, L, M or servo), so this class does not
 * either. {@link #isMoving()} reports the last command sent, because the receiver sends no feedback.
 */
public final class PFMotor implements DCMotor {
    private static final int MAX_POWER = 100;

    private final RemotePF remote;
    private final PFChannel pfChannel;
    private final PFPort pfPort;
    private int power = MAX_POWER;
    /** {@code 1} for forward, {@code -1} for backward, {@code 0} when not driving. */
    private int direction;
    private boolean moving;

    /** A motor on {@code pfPort} of the receiver on {@code pfChannel}, sent through {@code remote}. */
    public PFMotor(RemotePF remote, PFChannel pfChannel, PFPort pfPort) {
        this.remote = remote;
        this.pfChannel = pfChannel;
        this.pfPort = pfPort;
    }

    /** Sets the power, {@code 0..100}, applied at once if the motor is moving. */
    @Override
    public void setPower(int power) {
        this.power = power > MAX_POWER ? MAX_POWER : power < 0 ? 0 : power;
        if (moving) {
            drive();
        }
    }

    @Override
    public int getPower() {
        return power;
    }

    /** Runs the motor forward at the current power. */
    @Override
    public void forward() {
        direction = 1;
        drive();
    }

    /** Runs the motor backward at the current power. */
    @Override
    public void backward() {
        direction = -1;
        drive();
    }

    /** Actively brakes the motor, unlike {@link #flt()}, which lets it coast. */
    @Override
    public void stop() {
        remote.brake(pfChannel, pfPort);
        direction = 0;
        moving = false;
    }

    /** Lets the motor coast to a stop, unlike {@link #stop()}, which brakes it. */
    @Override
    public void flt() {
        remote.setSpeed(pfChannel, pfPort, 0);
        direction = 0;
        moving = false;
    }

    @Override
    public boolean isMoving() {
        return moving;
    }

    /** Sends a raw protocol speed, {@code -7..7}: positive forward, negative backward, {@code 0} coasts. */
    public void setSpeed(int speed) {
        remote.setSpeed(pfChannel, pfPort, speed);
        direction = speed > 0 ? 1 : speed < 0 ? -1 : 0;
        moving = direction != 0;
    }

    /** Sends the current direction at the speed step for the current power. */
    private void drive() {
        remote.setSpeed(pfChannel, pfPort, direction * speedStep());
        moving = direction != 0;
    }

    /** Rounds the power to the nearest of the seven speed steps. */
    private int speedStep() {
        return (power * RemotePF.MAX_SPEED + MAX_POWER / 2) / MAX_POWER;
    }
}
