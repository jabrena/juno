package io.github.jabrena.juno.hardware.device.motor;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.robotics.DCMotor;

/**
 * One brushed DC-motor channel of an L298N dual H-bridge module.
 *
 * <p>Connect {@code enablePin} to ENA or ENB (with that module's jumper removed), and {@code in1Pin}/{@code in2Pin}
 * to its two direction inputs. Create and {@link #begin() initialise} one channel for each yellow TT motor at startup.
 * Power is a PWM percentage; this open-loop driver deliberately implements {@link DCMotor}, rather than
 * {@code RegulatedMotor}, because the pictured TT motors have no encoder.
 */
public final class L298nMotor implements DCMotor {
    private static final int MAX_PWM = 255;

    private final Gpio gpio;
    private final int enablePin;
    private final int in1Pin;
    private final int in2Pin;
    private int power;
    private boolean moving;

    /** A motor driven through {@code gpio}; supply a mock in a unit test. */
    public L298nMotor(Gpio gpio, int enablePin, int in1Pin, int in2Pin) {
        if (enablePin < 0 || in1Pin < 0 || in2Pin < 0) {
            throw new IllegalArgumentException("L298N pins must be non-negative");
        }
        if (enablePin == in1Pin || enablePin == in2Pin || in1Pin == in2Pin) {
            throw new IllegalArgumentException("L298N enable and direction pins must be distinct");
        }
        this.gpio = gpio;
        this.enablePin = enablePin;
        this.in1Pin = in1Pin;
        this.in2Pin = in2Pin;
    }

    /** Configures the three pins and leaves the motor coasting. Call once at startup. */
    public void begin() {
        gpio.pinMode(enablePin, gpio.getOutput());
        gpio.pinMode(in1Pin, gpio.getOutput());
        gpio.pinMode(in2Pin, gpio.getOutput());
        flt();
    }

    /** Drives the motor forward at its configured power. */
    @Override
    public void forward() {
        drive(true);
    }

    /** Drives the motor backward at its configured power. */
    @Override
    public void backward() {
        drive(false);
    }

    /** Brakes the motor by driving both bridge inputs low while the bridge remains enabled. */
    @Override
    public void stop() {
        gpio.digitalWrite(in1Pin, false);
        gpio.digitalWrite(in2Pin, false);
        gpio.analogWrite(enablePin, MAX_PWM);
        moving = false;
    }

    /** Disables the bridge so the motor coasts. */
    @Override
    public void flt() {
        gpio.analogWrite(enablePin, 0);
        gpio.digitalWrite(in1Pin, false);
        gpio.digitalWrite(in2Pin, false);
        moving = false;
    }

    /** Changes the open-loop PWM power, clamped to {@code 0..100}. */
    @Override
    public void setPower(int power) {
        this.power = Math.max(0, Math.min(100, power));
        gpio.analogWrite(enablePin, this.power * MAX_PWM / 100);
        if (this.power == 0) {
            moving = false;
        }
    }

    /** Returns the configured PWM power percentage. */
    @Override
    public int getPower() {
        return power;
    }

    /** Returns whether this channel is driving the motor with non-zero power. */
    @Override
    public boolean isMoving() {
        return moving;
    }

    private void drive(boolean forward) {
        gpio.digitalWrite(in1Pin, forward);
        gpio.digitalWrite(in2Pin, !forward);
        gpio.analogWrite(enablePin, power * MAX_PWM / 100);
        moving = power > 0;
    }
}
