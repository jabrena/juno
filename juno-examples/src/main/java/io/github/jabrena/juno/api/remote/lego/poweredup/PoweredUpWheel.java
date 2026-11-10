package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpMotor;
import io.github.jabrena.juno.robotics.EncoderMotor;

/**
 * A Powered Up motor seen from the vehicle: forward is the wheel's forward, for the power and for the count, whichever
 * way the motor is mounted. A command is written to the hub only when it changes, since a
 * {@link io.github.jabrena.juno.robotics.navigation.DifferentialDrive} sets the power and the direction again every
 * control period and each command is a Bluetooth LE write. Create it once at startup.
 */
final class PoweredUpWheel implements EncoderMotor {
    /** No drive sent since the last stop: a drive command is never zero, so any one is sent. */
    private static final int STOPPED = 0;

    private final PoweredUpMotor motor;
    private final int direction;
    private int power;
    private int sent = STOPPED;

    /** {@code direction} is {@code 1} when the motor drives the wheel forward at a positive power, {@code -1} if mirrored. */
    PoweredUpWheel(PoweredUpMotor motor, int direction) {
        this.motor = motor;
        this.direction = direction;
    }

    @Override
    public void setPower(int power) {
        this.power = power;
    }

    @Override
    public int getPower() {
        return power;
    }

    @Override
    public void forward() {
        send(power);
    }

    @Override
    public void backward() {
        send(-power);
    }

    @Override
    public void stop() {
        motor.stop();
        sent = STOPPED;
    }

    @Override
    public void flt() {
        motor.flt();
        sent = STOPPED;
    }

    @Override
    public boolean isMoving() {
        return sent != STOPPED;
    }

    @Override
    public int getTachoCount() {
        return direction * motor.getTachoCount();
    }

    @Override
    public void resetTachoCount() {
        motor.resetTachoCount();
    }

    private void send(int signed) {
        if (signed != sent) {
            motor.drive(direction * signed);
            sent = signed;
        }
    }
}
