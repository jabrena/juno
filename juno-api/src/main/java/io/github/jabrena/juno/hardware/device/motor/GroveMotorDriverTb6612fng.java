package io.github.jabrena.juno.hardware.device.motor;

import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.robotics.DCMotor;

/**
 * Seeed Grove I2C Motor Driver (TB6612FNG): two brushed DC-motor channels, A and B, on one Grove I2C socket. The
 * driver's own microcontroller turns each I2C command into the TB6612FNG's direction and PWM signals, so the board
 * needs no motor pins. The motors need their own supply, 2.5 V to 13.5 V; the socket's 3.3 V or 5 V only powers the
 * driver's logic.
 *
 * <p>Create one driver at startup over the board's bus, {@code new GroveMotorDriverTb6612fng(MCU.getI2c())}, and use
 * {@link #getMotorA()} and {@link #getMotorB()} as {@link DCMotor}s. Power is a percentage, scaled to the driver's
 * 0..255 PWM. {@link DCMotor#stop()} brakes a channel, and {@link DCMotor#flt()} lets it coast. The driver answers at
 * address {@code 0x14} by default; pass the address it was given if it has been changed.
 *
 * <p>The constructor checks that the driver acknowledges on the bus, then takes it out of standby. Each later command
 * is sent once, without a check, as with the other I2C devices, so a driver unplugged after startup stops the motors
 * silently.
 */
public final class GroveMotorDriverTb6612fng {
    /** The driver's default 7-bit I2C address. */
    public static final int ADDRESS_DEFAULT = 0x14;

    private static final int CHANNEL_A = 0;
    private static final int CHANNEL_B = 1;
    private static final int CMD_BRAKE = 0x00;
    private static final int CMD_STOP = 0x01;
    private static final int CMD_CLOCKWISE = 0x02;
    private static final int CMD_COUNTER_CLOCKWISE = 0x03;
    private static final int CMD_NOT_STANDBY = 0x05;
    private static final int MAX_PWM = 255;

    private final I2c i2c;
    private final int address;
    private final byte[] frame = new byte[3];
    private final Channel motorA;
    private final Channel motorB;

    /** A driver at {@link #ADDRESS_DEFAULT} on {@code i2c}; pass a mock bus in a unit test. */
    public GroveMotorDriverTb6612fng(I2c i2c) {
        this(i2c, ADDRESS_DEFAULT);
    }

    /** A driver at {@code address}, {@code 0x01..0x7F}, on {@code i2c}. */
    public GroveMotorDriverTb6612fng(I2c i2c, int address) {
        if (address < 0x01 || address > 0x7F) {
            throw new IllegalArgumentException("Grove motor driver I2C address must be 0x01..0x7F");
        }
        this.i2c = i2c;
        this.address = address;
        this.motorA = new Channel(this, CHANNEL_A);
        this.motorB = new Channel(this, CHANNEL_B);
        begin();
    }

    /** Starts the bus, checks that the driver answers, then leaves standby. */
    private void begin() {
        i2c.begin();
        if (i2c.write(address, frame, 0) < 0) {
            throw new IllegalStateException("Grove motor driver did not acknowledge on the I2C bus");
        }
        frame[0] = CMD_NOT_STANDBY;
        frame[1] = 0;
        i2c.write(address, frame, 2);
    }

    /** Channel A, the first motor socket on the driver. The same instance every call. */
    public DCMotor getMotorA() {
        return motorA;
    }

    /** Channel B, the second motor socket on the driver. The same instance every call. */
    public DCMotor getMotorB() {
        return motorB;
    }

    private void drive(int channel, boolean clockwise, int power) {
        frame[0] = (byte) (clockwise ? CMD_CLOCKWISE : CMD_COUNTER_CLOCKWISE);
        frame[1] = (byte) channel;
        frame[2] = (byte) (power * MAX_PWM / 100);
        i2c.write(address, frame, 3);
    }

    private void brake(int channel) {
        frame[0] = CMD_BRAKE;
        frame[1] = (byte) channel;
        i2c.write(address, frame, 2);
    }

    private void coast(int channel) {
        frame[0] = CMD_STOP;
        frame[1] = (byte) channel;
        i2c.write(address, frame, 2);
    }

    /** One motor socket of the driver, as a {@link DCMotor}. */
    private static final class Channel implements DCMotor {
        private final GroveMotorDriverTb6612fng driver;
        private final int channel;
        private int power;
        private boolean clockwise = true;
        private boolean moving;

        private Channel(GroveMotorDriverTb6612fng driver, int channel) {
            this.driver = driver;
            this.channel = channel;
        }

        /** Turns the motor clockwise at the configured power. */
        @Override
        public void forward() {
            run(true);
        }

        /** Turns the motor counter-clockwise at the configured power. */
        @Override
        public void backward() {
            run(false);
        }

        /** Brakes the motor; it stays held in place. */
        @Override
        public void stop() {
            driver.brake(channel);
            moving = false;
        }

        /** Lets the motor coast to a stop. */
        @Override
        public void flt() {
            driver.coast(channel);
            moving = false;
        }

        /** Changes the power, clamped to {@code 0..100}; a moving motor speeds up or slows down at once. */
        @Override
        public void setPower(int power) {
            this.power = Math.max(0, Math.min(100, power));
            if (moving) {
                run(clockwise);
            }
        }

        /** Returns the configured power percentage, {@code 0..100}. */
        @Override
        public int getPower() {
            return power;
        }

        /** Returns whether the motor is commanded to turn with non-zero power. */
        @Override
        public boolean isMoving() {
            return moving;
        }

        private void run(boolean clockwise) {
            this.clockwise = clockwise;
            driver.drive(channel, clockwise, power);
            moving = power > 0;
        }
    }
}
