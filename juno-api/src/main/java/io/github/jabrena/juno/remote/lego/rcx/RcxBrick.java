package io.github.jabrena.juno.remote.lego.rcx;

/**
 * The RCX's direct commands that answer back, the protocol under {@link RemoteRcx}'s motors, sensors, display, sound
 * and battery (as LeJOS builds {@code RemoteNXT} on {@code NXTCommand}); use {@link RemoteRcx} in a program, and this
 * class to share the commands with another brick of the family, as {@code RemoteScout} does. Set up the link with
 * {@link RemoteRcx#begin} or {@link RcxLink#begin} first, with both the IR LED and the IR receiver module wired. Plain
 * Java on {@link io.github.jabrena.juno.hardware.device.ir.Infrared}.
 *
 * <p>Each call is a request and a reply, which takes 100 ms or more; they return {@code -1} when the
 * brick does not answer (out of range, off, or the IR LED not pointing at it). The opcode and argument encodings have
 * not been verified on a brick.
 */
public final class RcxBrick {
    private final RcxLink link;

    /** A brick on the board's own infrared link. Create it once, at startup. */
    public RcxBrick() {
        this(new RcxLink());
    }

    /** A brick on a link shared with other RCX classes, so they share one toggle bit. */
    public RcxBrick(RcxLink link) {
        this.link = link;
    }


    /** The RCX's three sensor inputs, numbered 1 to 3 on the brick. */
    public static final int INPUT_1 = 0;
    public static final int INPUT_2 = 1;
    public static final int INPUT_3 = 2;

    /** The RCX's three outputs, as bits so that several can be combined, e.g. {@code OUTPUT_A | OUTPUT_B}. */
    public static final int OUTPUT_A = 1;
    public static final int OUTPUT_B = 2;
    public static final int OUTPUT_C = 4;

    private static final int OPCODE_ON_OFF_FLOAT = 0x21;
    private static final int OPCODE_OUTPUT_DIRECTION = 0xE1;
    private static final int OPCODE_OUTPUT_POWER = 0x13;
    private static final int OPCODE_GLOBAL_OUTPUT_POWER = 0xA3;
    private static final int OPCODE_GLOBAL_OUTPUT_MODE = 0x67;
    private static final int OPCODE_GLOBAL_OUTPUT_DIRECTION = 0x77;
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
    /** Juno's firmware only: an opcode LEGO's firmware does not use, whose low bits give it two arguments. */
    private static final int OPCODE_SHOW_NUMBER = 0x72;
    /** Juno's firmware's icon command; the brick never delivers a frame whose opcode byte is exactly 0xA2. */
    private static final int OPCODE_SET_ICON = 0xB2;
    /**
     * Juno's firmware's text command: a position 0-3 and an ASCII character, or a position past 3 to blank all four.
     * The ROM takes an opcode's low three bits as its argument count, so both arguments are always sent.
     */
    private static final int OPCODE_SET_CHARACTER = 0x82;
    private static final int TEXT_POSITIONS = 4;
    private static final int BLANK_ALL = 0xFF;
    private static final int OPCODE_SET_INPUT_TYPE = 0x32;
    private static final int OPCODE_SET_INPUT_MODE = 0x42;
    private static final int SOURCE_SENSOR_VALUE = 9;
    /** Juno's firmware's poll source for the brick's clicked buttons; LEGO's firmware has none. */
    private static final int SOURCE_CLICKED_BUTTONS = 0x60;
    private static final int TYPE_TOUCH = 1;
    private static final int MODE_BOOLEAN = 0x20;

    /**
     * Configures {@code input} for a touch sensor reading {@code 1} when pressed and {@code 0}
     * otherwise. Returns whether the brick acknowledged both commands.
     */
    public boolean setTouchSensor(int input) {
        boolean typed = link.request(OPCODE_SET_INPUT_TYPE, input, TYPE_TOUCH, 2) >= 0;
        boolean moded = link.request(OPCODE_SET_INPUT_MODE, input, MODE_BOOLEAN, 2) >= 0;
        return typed && moded;
    }

    /** The value of the sensor on {@code input} (for a touch sensor 1 pressed, 0 released), or {@code -1} if no reply. */
    public int readSensor(int input) {
        int arguments = link.request(OPCODE_POLL, SOURCE_SENSOR_VALUE, input, 2);
        return arguments < 2 ? -1 : link.argument0 | (link.argument1 << 8);
    }

    /**
     * Configures {@code input} for a light sensor reading the light level as a percentage, {@code 0..100}, which
     * {@link #readSensor} then returns. Returns whether the brick acknowledged both commands.
     */
    public boolean setLightSensor(int input) {
        return configureSensor(input, TYPE_LIGHT, MODE_PERCENT);
    }

    /**
     * Configures {@code input} for a rotation sensor counting {@value #STEPS_PER_TURN} steps per revolution, which
     * {@link #rotationSteps} and {@link #rotationDegrees} read; {@link #clearSensor} sets it back to zero.
     */
    public boolean setRotationSensor(int input) {
        return configureSensor(input, TYPE_ROTATION, MODE_ANGLE_STEPS);
    }

    /** Configures {@code input} for a temperature sensor, read in tenths of a degree Celsius by {@link #temperatureTenths}. */
    public boolean setTemperatureSensor(int input) {
        return configureSensor(input, TYPE_TEMPERATURE, MODE_CELSIUS);
    }

    /** The signed rotation count of the sensor on {@code input} in steps (16 per turn), or {@code 0} if the brick did not answer. */
    public int rotationSteps(int input) {
        return signed16(readSensor(input));
    }

    /** The rotation of the sensor on {@code input} in degrees, as 22.5 degrees per step rounded down. */
    public int rotationDegrees(int input) {
        return rotationSteps(input) * 360 / STEPS_PER_TURN;
    }

    /** The temperature on {@code input} in tenths of a degree Celsius (235 is 23.5 degrees), or {@code 0} if the brick did not answer. */
    public int temperatureTenths(int input) {
        return signed16(readSensor(input));
    }

    /** Sets the value of the sensor on {@code input} back to zero, e.g. a rotation sensor's count. */
    public void clearSensor(int input) {
        link.command(OPCODE_CLEAR_SENSOR, input, 0, 0, 1);
    }

    /** Turns the outputs {@code outputs} (OUTPUT_* bits) on, at the power level and in the direction last set. */
    public void motorOn(int outputs) {
        link.command(OPCODE_ON_OFF_FLOAT, MODE_ON | (outputs & 0x7), 0, 0, 1);
    }

    /** Switches the outputs off with the motors braking. */
    public void motorOff(int outputs) {
        link.command(OPCODE_ON_OFF_FLOAT, MODE_OFF | (outputs & 0x7), 0, 0, 1);
    }

    /** Switches the outputs off with the motors free to coast. */
    public void motorFloat(int outputs) {
        link.command(OPCODE_ON_OFF_FLOAT, MODE_FLOAT | (outputs & 0x7), 0, 0, 1);
    }

    /** Sets the direction of the outputs, forwards or backwards. */
    public void setMotorDirection(int outputs, boolean forward) {
        link.command(OPCODE_OUTPUT_DIRECTION, (forward ? DIRECTION_FORWARD : DIRECTION_BACKWARD) | (outputs & 0x7), 0, 0, 1);
    }

    /** Sets the power level of the outputs, {@code 0..}{@link RcxMotor#MAX_POWER} (clamped); it takes effect while they are on. */
    public void setMotorPower(int outputs, int level) {
        int limited = level < 0 ? 0 : level > RcxMotor.MAX_POWER ? RcxMotor.MAX_POWER : level;
        link.command(OPCODE_OUTPUT_POWER, outputs & 0x7, SOURCE_CONSTANT, limited, 3);
    }

    /** Turns the outputs on with the global output command implemented by RCX 2.0, Scout and Spybot firmware. */
    public void globalMotorOn(int outputs) {
        setGlobalMotorMode(outputs, MODE_ON);
    }

    /** Brakes the outputs with the global output command implemented by RCX 2.0, Scout and Spybot firmware. */
    public void globalMotorOff(int outputs) {
        setGlobalMotorMode(outputs, MODE_OFF);
    }

    /** Lets the outputs coast with the global output command implemented by RCX 2.0, Scout and Spybot firmware. */
    public void globalMotorFloat(int outputs) {
        setGlobalMotorMode(outputs, MODE_FLOAT);
    }

    /** Sets global output direction using the command implemented by RCX 2.0, Scout and Spybot firmware. */
    public void setGlobalMotorDirection(int outputs, boolean forward) {
        link.command(OPCODE_GLOBAL_OUTPUT_DIRECTION,
                (forward ? DIRECTION_FORWARD : DIRECTION_BACKWARD) | (outputs & 0x7), 0, 0, 1);
    }

    /** Sets the maximum output power with the command implemented by RCX 2.0, Scout and Spybot firmware. */
    public void setMaxMotorPower(int outputs, int level) {
        int limited = level < 0 ? 0 : level > RcxMotor.MAX_POWER ? RcxMotor.MAX_POWER : level;
        link.command(OPCODE_GLOBAL_OUTPUT_POWER, outputs & 0x7, SOURCE_CONSTANT, limited, 3);
    }

    /**
     * Runs the outputs at {@code speed}, {@code -7..7}: the sign is the direction, the size the power level, and
     * {@code 0} brakes them. That is up to four commands, each waiting for the brick's acknowledgement when the
     * receiver is wired, so one call takes a few hundred milliseconds; set the speed once and not in a tight loop.
     */
    public void drive(int outputs, int speed) {
        if (speed == 0) {
            motorOff(outputs);
            return;
        }
        setMotorDirection(outputs, speed > 0);
        setMotorPower(outputs, speed > 0 ? speed : -speed);
        motorOn(outputs);
    }

    private void setGlobalMotorMode(int outputs, int mode) {
        link.command(OPCODE_GLOBAL_OUTPUT_MODE, mode | (outputs & 0x7), 0, 0, 1);
    }

    private boolean configureSensor(int input, int type, int mode) {
        boolean typed = link.request(OPCODE_SET_INPUT_TYPE, input, type, 2) >= 0;
        boolean moded = link.request(OPCODE_SET_INPUT_MODE, input, mode, 2) >= 0;
        return typed && moded;
    }

    private int signed16(int value) {
        return value >= 0x8000 ? value - 0x10000 : value;
    }

    /**
     * The brick's battery voltage in millivolts (about 9000 with fresh batteries, and the brick warns
     * below about 6000), or {@code -1} if it did not answer. It is a request and a reply, like the sensor reads.
     */
    public int batteryMillivolts() {
        int arguments = link.request(OPCODE_BATTERY_LEVEL, 0, 0, 0);
        return arguments < 2 ? -1 : link.argument0 | (link.argument1 << 8);
    }

    /**
     * Plays one of the six built-in sounds, {@code RcxAudio.SOUND_*}. Returns whether the brick acknowledged the command, which
     * needs the IR receiver wired; the sound then plays on the brick's own speaker and the call returns without
     * waiting for it to end.
     */
    public boolean playSound(int sound) {
        return link.request(OPCODE_PLAY_SOUND, sound & 0xFF, 0, 1) >= 0;
    }

    /**
     * Shows {@code value}, limited to {@link RcxLcd#MIN_NUMBER}..{@link RcxLcd#MAX_NUMBER}, on the brick's display in place of
     * {@code JUno}, until the next call. Needs Juno's own RCX firmware ({@code RcxRemoteFirmware}); LEGO's
     * firmware does not know the command and does not answer. Returns whether the brick acknowledged.
     */
    public boolean showNumber(int value) {
        int limited = value < RcxLcd.MIN_NUMBER ? RcxLcd.MIN_NUMBER : value > RcxLcd.MAX_NUMBER ? RcxLcd.MAX_NUMBER : value;
        return link.request(OPCODE_SHOW_NUMBER, limited & 0xFF, (limited >> 8) & 0xFF, 2) >= 0;
    }

    /**
     * Blanks the display, its number and icons too (but for the IR cones), until a number or text is shown. Needs
     * Juno's own RCX firmware ({@code RcxRemoteFirmware}); LEGO's firmware does not answer. Returns whether the brick
     * acknowledged.
     */
    public boolean clearDisplay() {
        return link.request(OPCODE_SET_CHARACTER, BLANK_ALL, 0, 2) >= 0;
    }

    /**
     * Shows the first four characters of {@code text} on the display's four digits, left to right, blank past its
     * end, as a seven-segment display draws them (letters such as V, W and M are approximated, and characters it
     * cannot draw show blank). Sends one command per digit; needs Juno's own RCX firmware. Returns whether the brick
     * acknowledged every one.
     */
    public boolean showText(String text) {
        boolean acknowledged = true;
        for (int position = 0; position < TEXT_POSITIONS; position++) {
            int character = position < text.length() ? text.charAt(position) & 0x7F : ' ';
            acknowledged &= link.request(OPCODE_SET_CHARACTER, position, character, 2) >= 0;
        }
        return acknowledged;
    }

    /**
     * Lights the display icon {@code icon}, {@code RcxLcd.ICON_*}, or with {@code lit} false hands it back to the brick, which
     * lights some icons from its own state: running motors' arrows, pressed touch sensors, low battery, and the
     * minifigure, standing and walking while a motor runs. Needs Juno's own RCX firmware ({@code RcxRemoteFirmware});
     * LEGO's firmware does not know the command and does not answer. Returns whether the brick acknowledged.
     */
    public boolean showIcon(int icon, boolean lit) {
        return link.request(OPCODE_SET_ICON, icon & 0xFF, lit ? 1 : 0, 2) >= 0;
    }

    /**
     * Plays a tone of {@code frequencyHz} (about 31 to 20000 Hz) for {@code durationCentiseconds} hundredths
     * of a second ({@code 1..255}, so up to 2.55 s), then returns without waiting for it to end. Returns
     * whether the brick acknowledged the command. A tone replaces any sound still playing, so wait for the
     * duration before sending the next.
     */
    public boolean playTone(int frequencyHz, int durationCentiseconds) {
        return link.request(OPCODE_PLAY_TONE, frequencyHz & 0xFF, (frequencyHz >> 8) & 0xFF,
                durationCentiseconds & 0xFF, 3) >= 0;
    }

    /**
     * The brick's own buttons clicked since the previous call, Run 0x01, View 0x02, Prgm 0x04, or {@code -1} if no
     * reply. Polls Juno's own source {@value #SOURCE_CLICKED_BUTTONS}, which only Juno's RCX firmware answers.
     */
    public int clickedButtons() {
        int arguments = link.request(OPCODE_POLL, SOURCE_CLICKED_BUTTONS, 0, 2);
        return arguments < 2 ? -1 : link.argument0 | (link.argument1 << 8);
    }

    /** Whether the touch sensor on {@code input} is pressed; {@code false} also when the brick did not answer. */
    public boolean isPressed(int input) {
        return readSensor(input) == 1;
    }
}
