package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.robotics.EncoderMotor;

/**
 * The motor on one external port of a Powered Up hub, as an {@link EncoderMotor}. Get it from
 * {@link RemotePoweredUpHub#getMotor}, which returns the same object for a port on every call.
 *
 * <p>The hub reports the position once the first read asks for it, and again after each reconnect.
 * {@link #getTachoCount} counts from zero at {@link #resetTachoCount}, which keeps the hub's own count and only
 * remembers an offset, since the hub has no reset command.
 */
public final class PoweredUpMotor implements EncoderMotor {
    private static final int MAX_POWER = 100;

    private final PoweredUpTransport hubPort;
    private final PoweredUpPort port;
    private int power = MAX_POWER;
    /** {@code 1} for forward, {@code -1} for backward, {@code 0} when not driving. */
    private int direction;
    private boolean moving;
    private boolean subscribed;
    private int zeroOffset;

    PoweredUpMotor(PoweredUpTransport hubPort, PoweredUpPort port) {
        this.hubPort = hubPort;
        this.port = port;
    }

    /** Called by the hub on every connect: the next position read subscribes to the hub's reports again. */
    void onConnect() {
        subscribed = false;
    }

    /** The hub's raw position, subscribing to its reports on the first read after each connect. */
    private int position() {
        if (!subscribed) {
            hubPort.enableSensor(port.value(), RemotePoweredUpHub.MODE_MOTOR_POSITION);
            subscribed = true;
        }
        return hubPort.readSensor(port.value());
    }

    /**
     * Runs the motor at {@code percent}, {@code -100..100} (clamped): the sign is the direction, the size the power,
     * and {@code 0} lets it coast.
     */
    public void drive(int percent) {
        int limited = percent > MAX_POWER ? MAX_POWER : percent < -MAX_POWER ? -MAX_POWER : percent;
        if (limited == 0) {
            flt();
            return;
        }
        power = limited > 0 ? limited : -limited;
        direction = limited > 0 ? 1 : -1;
        drive();
    }

    /** Sets the power, {@code 0..100} (clamped), applied at once if the motor is moving. */
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
        hubPort.brakeMotor(port.value());
        direction = 0;
        moving = false;
    }

    /** Lets the motor coast to a stop, unlike {@link #stop()}, which brakes it. */
    @Override
    public void flt() {
        hubPort.setMotorPower(port.value(), 0);
        direction = 0;
        moving = false;
    }

    /**
     * Holds the motor where it is, resisting being turned: a speed command of 0 that the hub's speed control holds.
     * The message follows the protocol documentation and has not been checked systematically on a hub. Any later power
     * command releases the hold.
     */
    public void hold() {
        hubPort.holdMotor(port.value());
        direction = 0;
        moving = false;
    }

    @Override
    public boolean isMoving() {
        return moving;
    }

    /**
     * The position in degrees since the last {@link #resetTachoCount}, or since the hub's zero if never reset. The hub
     * reports positions while a hub call runs, so call it regularly; it reads {@code 0} before the first report.
     */
    @Override
    public int getTachoCount() {
        return position() - zeroOffset;
    }

    /** Makes the current position read as zero. */
    @Override
    public void resetTachoCount() {
        zeroOffset = position();
    }

    /** Sends the current direction at the current power. */
    private void drive() {
        hubPort.setMotorPower(port.value(), direction * power);
        moving = direction != 0;
    }
}
