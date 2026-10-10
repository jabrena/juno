package io.github.jabrena.juno.api.lego;

/**
 * Direct commands to an RCX brick that answer back, over the same infrared link as
 * {@link RcxRemote} (set it up with {@link RcxRemote#begin} first, with both the IR LED and the IR
 * receiver module wired). It reads the touch sensors plugged into the RCX's own inputs, so a vehicle
 * can sense obstacles without extra wiring to the Arduino. Plain Java on {@link io.github.jabrena.juno.api.io.ir.Infrared}.
 *
 * <p>Each call is a request and a reply, which takes 100 ms or more; they return {@code -1} when the
 * brick does not answer (out of range, off, or the IR LED not pointing at it). The opcode and
 * argument encodings come from the published RCX opcode tables and have not been verified on a brick.
 */
public final class RcxBrick {
    private RcxBrick() {
    }

    /** The RCX's three sensor inputs, numbered 1 to 3 on the brick. */
    public static final int INPUT_1 = 0;
    public static final int INPUT_2 = 1;
    public static final int INPUT_3 = 2;

    /** The six built-in sounds for {@link #playSound}. */
    public static final int SOUND_KEY_CLICK = 0;
    public static final int SOUND_BEEP = 1;
    public static final int SOUND_SWEEP_DOWN = 2;
    public static final int SOUND_SWEEP_UP = 3;
    public static final int SOUND_ERROR = 4;
    public static final int SOUND_FAST_SWEEP_UP = 5;

    /** The RCX's three outputs, as bits so that several can be combined, e.g. {@code OUTPUT_A | OUTPUT_B}. */
    public static final int OUTPUT_A = 1;
    public static final int OUTPUT_B = 2;
    public static final int OUTPUT_C = 4;

    /** The highest power level of {@link #setMotorPower}. */
    public static final int MAX_POWER = 7;

    private static final int OPCODE_ON_OFF_FLOAT = 0x21;
    private static final int OPCODE_OUTPUT_DIRECTION = 0xE1;
    private static final int OPCODE_OUTPUT_POWER = 0x13;
    private static final int OPCODE_CLEAR_SENSOR = 0xD1;
    private static final int MODE_ON = 0x80;
    private static final int MODE_OFF = 0x40;
    private static final int MODE_FLOAT = 0x00;
    private static final int DIRECTION_FORWARD = 0x80;
    private static final int DIRECTION_BACKWARD = 0x00;
    private static final int SOURCE_CONSTANT = 2;
    private static final int TYPE_TEMPERATURE = 2;
    private static final int TYPE_LIGHT = 3;
    private static final int TYPE_ROTATION = 4;
    private static final int MODE_PERCENT = 0x80;
    private static final int MODE_CELSIUS = 0xA0;
    private static final int MODE_ANGLE_STEPS = 0xE0;
    private static final int STEPS_PER_TURN = 16;
    private static final int OPCODE_BATTERY_LEVEL = 0x30;
    private static final int OPCODE_PLAY_SOUND = 0x51;
    private static final int OPCODE_PLAY_TONE = 0x23;
    private static final int OPCODE_POLL = 0x12;
    private static final int OPCODE_SET_INPUT_TYPE = 0x32;
    private static final int OPCODE_SET_INPUT_MODE = 0x42;
    private static final int SOURCE_SENSOR_VALUE = 9;
    private static final int TYPE_TOUCH = 1;
    private static final int MODE_BOOLEAN = 0x20;

    /**
     * Configures {@code input} for a touch sensor reading {@code 1} when pressed and {@code 0}
     * otherwise. Returns whether the brick acknowledged both commands.
     */
    public static boolean setTouchSensor(int input) {
        boolean typed = RcxLink.request(OPCODE_SET_INPUT_TYPE, input, TYPE_TOUCH, 2) >= 0;
        boolean moded = RcxLink.request(OPCODE_SET_INPUT_MODE, input, MODE_BOOLEAN, 2) >= 0;
        return typed && moded;
    }

    /** The value of the sensor on {@code input} (for a touch sensor 1 pressed, 0 released), or {@code -1} if no reply. */
    public static int readSensor(int input) {
        int arguments = RcxLink.request(OPCODE_POLL, SOURCE_SENSOR_VALUE, input, 2);
        return arguments < 2 ? -1 : RcxLink.argument0 | (RcxLink.argument1 << 8);
    }

    /**
     * Configures {@code input} for a light sensor reading the light level as a percentage, {@code 0..100}, which
     * {@link #readSensor} then returns. Returns whether the brick acknowledged both commands.
     */
    public static boolean setLightSensor(int input) {
        return configureSensor(input, TYPE_LIGHT, MODE_PERCENT);
    }

    /**
     * Configures {@code input} for a rotation sensor counting {@value #STEPS_PER_TURN} steps per revolution, which
     * {@link #rotationSteps} and {@link #rotationDegrees} read; {@link #clearSensor} sets it back to zero.
     */
    public static boolean setRotationSensor(int input) {
        return configureSensor(input, TYPE_ROTATION, MODE_ANGLE_STEPS);
    }

    /** Configures {@code input} for a temperature sensor, read in tenths of a degree Celsius by {@link #temperatureTenths}. */
    public static boolean setTemperatureSensor(int input) {
        return configureSensor(input, TYPE_TEMPERATURE, MODE_CELSIUS);
    }

    /** The signed rotation count of the sensor on {@code input} in steps (16 per turn), or {@code 0} if the brick did not answer. */
    public static int rotationSteps(int input) {
        return signed16(readSensor(input));
    }

    /** The rotation of the sensor on {@code input} in degrees, as 22.5 degrees per step rounded down. */
    public static int rotationDegrees(int input) {
        return rotationSteps(input) * 360 / STEPS_PER_TURN;
    }

    /** The temperature on {@code input} in tenths of a degree Celsius (235 is 23.5 degrees), or {@code 0} if the brick did not answer. */
    public static int temperatureTenths(int input) {
        return signed16(readSensor(input));
    }

    /** Sets the value of the sensor on {@code input} back to zero, e.g. a rotation sensor's count. */
    public static void clearSensor(int input) {
        RcxLink.command(OPCODE_CLEAR_SENSOR, input, 0, 0, 1);
    }

    /** Turns the outputs {@code outputs} (OUTPUT_* bits) on, at the power level and in the direction last set. */
    public static void motorOn(int outputs) {
        RcxLink.command(OPCODE_ON_OFF_FLOAT, MODE_ON | (outputs & 0x7), 0, 0, 1);
    }

    /** Switches the outputs off with the motors braking. */
    public static void motorOff(int outputs) {
        RcxLink.command(OPCODE_ON_OFF_FLOAT, MODE_OFF | (outputs & 0x7), 0, 0, 1);
    }

    /** Switches the outputs off with the motors free to coast. */
    public static void motorFloat(int outputs) {
        RcxLink.command(OPCODE_ON_OFF_FLOAT, MODE_FLOAT | (outputs & 0x7), 0, 0, 1);
    }

    /** Sets the direction of the outputs, forwards or backwards. */
    public static void setMotorDirection(int outputs, boolean forward) {
        RcxLink.command(OPCODE_OUTPUT_DIRECTION, (forward ? DIRECTION_FORWARD : DIRECTION_BACKWARD) | (outputs & 0x7), 0, 0, 1);
    }

    /** Sets the power level of the outputs, {@code 0..MAX_POWER} (clamped); it takes effect while they are on. */
    public static void setMotorPower(int outputs, int level) {
        int limited = level < 0 ? 0 : level > MAX_POWER ? MAX_POWER : level;
        RcxLink.command(OPCODE_OUTPUT_POWER, outputs & 0x7, SOURCE_CONSTANT, limited, 3);
    }

    /**
     * Runs the outputs at {@code speed}, {@code -7..7}: the sign is the direction, the size the power level, and
     * {@code 0} brakes them. That is up to four commands, each waiting for the brick's acknowledgement when the
     * receiver is wired, so one call takes a few hundred milliseconds; set the speed once and not in a tight loop.
     */
    public static void drive(int outputs, int speed) {
        if (speed == 0) {
            motorOff(outputs);
            return;
        }
        setMotorDirection(outputs, speed > 0);
        setMotorPower(outputs, speed > 0 ? speed : -speed);
        motorOn(outputs);
    }

    private static boolean configureSensor(int input, int type, int mode) {
        boolean typed = RcxLink.request(OPCODE_SET_INPUT_TYPE, input, type, 2) >= 0;
        boolean moded = RcxLink.request(OPCODE_SET_INPUT_MODE, input, mode, 2) >= 0;
        return typed && moded;
    }

    private static int signed16(int value) {
        return value >= 0x8000 ? value - 0x10000 : value;
    }

    /**
     * The brick's battery voltage in millivolts (about 9000 with fresh batteries, and the brick warns
     * below about 6000), or {@code -1} if it did not answer. It is a request and a reply, like the sensor reads.
     */
    public static int batteryMillivolts() {
        int arguments = RcxLink.request(OPCODE_BATTERY_LEVEL, 0, 0, 0);
        return arguments < 2 ? -1 : RcxLink.argument0 | (RcxLink.argument1 << 8);
    }

    /**
     * Plays one of the six built-in sounds, {@code SOUND_*}. Returns whether the brick acknowledged the command, which
     * needs the IR receiver wired; the sound then plays on the brick's own speaker and the call returns without
     * waiting for it to end.
     */
    public static boolean playSound(int sound) {
        return RcxLink.request(OPCODE_PLAY_SOUND, sound & 0xFF, 0, 1) >= 0;
    }

    /**
     * Plays a tone of {@code frequencyHz} (about 31 to 20000 Hz) for {@code durationCentiseconds} hundredths
     * of a second ({@code 1..255}, so up to 2.55 s), then returns without waiting for it to end. Returns
     * whether the brick acknowledged the command. A tone replaces any sound still playing, so wait for the
     * duration before sending the next.
     */
    public static boolean playTone(int frequencyHz, int durationCentiseconds) {
        return RcxLink.request(OPCODE_PLAY_TONE, frequencyHz & 0xFF, (frequencyHz >> 8) & 0xFF,
                durationCentiseconds & 0xFF, 3) >= 0;
    }

    /** Whether the touch sensor on {@code input} is pressed; {@code false} also when the brick did not answer. */
    public static boolean isPressed(int input) {
        return readSensor(input) == 1;
    }
}
