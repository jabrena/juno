package io.github.jabrena.juno.api.thirdparty.servo;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.util.Delay;

/**
 * The "16 x 12-bit PWM &amp; Servo Shield": an NXP PCA9685 that generates sixteen servo signals on its own, driven
 * over I2C as plain Java on top of {@link I2c} (so it needs no Arduino library). A servo is just a channel number,
 * {@code 0..15}, so nothing is allocated after the shield itself:
 *
 * <pre>{@code
 * PwmServoShield shield = new PwmServoShield();   // once, at startup
 * shield.begin();
 * shield.write(0, 90);          // servo on channel 0 to its centre
 * shield.writeMicros(1, 1500);  // or a raw pulse width
 * shield.release(0);            // stop driving it; the servo no longer holds its position
 * }</pre>
 *
 * <p>{@link #write(int, int)} takes the same {@code 0..180} degrees as {@link Servo#write(int)} and maps them onto
 * the same pulse widths the Arduino {@code Servo} library uses, 544 to 2400 microseconds, so a program moves from an
 * onboard pin to the shield without changing its angles.
 *
 * <p>The servos are powered from the shield's green terminal block (5-6V), never from the board: its {@code V+}
 * pins are fed only from there. Each channel is a column of three pins, {@code GND} nearest the board edge,
 * {@code V+} in the middle and {@code PWM} nearest the chip, matching a servo's brown, red and yellow wires. The
 * shield answers at address {@code 0x40}; bridging its address pads selects another, given to {@link #begin(int)}.
 * Works on every board, wherever {@code Wire} reaches the shield's SDA/SCL. {@code new PwmServoShield(i2c)} talks to
 * the shield through a given {@link I2c} instead of the board's, which is how a unit test mocks it.
 *
 * @see <a href="https://www.nxp.com/docs/en/data-sheet/PCA9685.pdf">PCA9685 data sheet</a>
 */
public final class PwmServoShield {
    public static final int ADDRESS_DEFAULT = 0x40;
    public static final int CHANNELS = 16;
    public static final int MIN_ANGLE = 0;
    public static final int MAX_ANGLE = 180;

    private static final int MODE1 = 0x00;
    private static final int CHANNEL_0_ON_L = 0x06;
    private static final int PRESCALE = 0xFE;
    private static final int MODE1_SLEEP = 0x10;
    private static final int MODE1_AWAKE = 0x00;
    // 25 MHz / (4096 ticks * 50 Hz) - 1: the 20 ms frame hobby servos expect.
    private static final int PRESCALE_50_HZ = 121;
    private static final int OSCILLATOR_WAKE_MILLIS = 1;
    private static final int FRAME_MICROS = 20000;
    private static final int TICKS_PER_FRAME = 4096;
    private static final int FULL_OFF = 0x10;
    // The Arduino Servo library's defaults, so Servo.write and write agree on what an angle means.
    private static final int MIN_PULSE_MICROS = 544;
    private static final int MAX_PULSE_MICROS = 2400;

    private final I2c i2c;
    private int address = ADDRESS_DEFAULT;

    /** A shield on the board's own I2C bus. Create it once, at startup. */
    public PwmServoShield() {
        this(MCU.getI2c());
    }

    /** A shield driven through {@code i2c}: a mocked one in a unit test. */
    public PwmServoShield(I2c i2c) {
        this.i2c = i2c;
    }

    /** Starts I2C and sets the shield at {@code 0x40} to the 50 Hz servo frame. */
    public void begin() {
        begin(ADDRESS_DEFAULT);
    }

    /** Starts I2C and sets the shield at {@code shieldAddress} to the 50 Hz servo frame. */
    public void begin(int shieldAddress) {
        address = shieldAddress;
        i2c.begin();
        // The prescaler can only be written while the oscillator sleeps.
        i2c.writeRegister(address, MODE1, MODE1_SLEEP);
        i2c.writeRegister(address, PRESCALE, PRESCALE_50_HZ);
        i2c.writeRegister(address, MODE1, MODE1_AWAKE);
        Delay.millis(OSCILLATOR_WAKE_MILLIS);
    }

    /** Turns the servo on {@code channel} to {@code angleDegrees}, clamped to {@code 0..180}. */
    public void write(int channel, int angleDegrees) {
        int angle = Math.max(MIN_ANGLE, Math.min(MAX_ANGLE, angleDegrees));
        writeMicros(channel, MIN_PULSE_MICROS + angle * (MAX_PULSE_MICROS - MIN_PULSE_MICROS) / MAX_ANGLE);
    }

    /** Sends {@code channel} a pulse of {@code micros} microseconds every 20 ms, clamped to the frame. */
    public void writeMicros(int channel, int micros) {
        int clamped = Math.max(0, Math.min(FRAME_MICROS, micros));
        writeChannel(channel, 0, clamped * (TICKS_PER_FRAME - 1) / FRAME_MICROS);
    }

    /** Stops the pulses on {@code channel}: the servo goes limp instead of holding its position. */
    public void release(int channel) {
        writeChannel(channel, 0, FULL_OFF << 8);
    }

    /** The channel goes high at {@code onTick} of each frame's 4096 ticks and low at {@code offTick}. */
    private void writeChannel(int channel, int onTick, int offTick) {
        int register = CHANNEL_0_ON_L + 4 * channel;
        i2c.writeRegister(address, register, onTick & 0xFF);
        i2c.writeRegister(address, register + 1, onTick >> 8);
        i2c.writeRegister(address, register + 2, offTick & 0xFF);
        i2c.writeRegister(address, register + 3, offTick >> 8);
    }
}
