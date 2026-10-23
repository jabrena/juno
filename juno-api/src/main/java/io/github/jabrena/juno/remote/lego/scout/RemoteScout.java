package io.github.jabrena.juno.remote.lego.scout;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.remote.lego.rcx.RcxBrick;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxSensor;

/**
 * Remote control of a LEGO Scout brick over infrared. Create it once at startup, call {@link #begin(int, int)}, and
 * use its motors ({@link #getMotorA()}, {@link #getMotorB()}) and sensor inputs ({@link #getSensor1()},
 * {@link #getSensor2()}). The parts share one {@link RcxLink}, so commands use one toggle bit and getting a part
 * allocates nothing. It also sends the RCX remote-control button and message commands accepted by Scout firmware.
 *
 * <p>The link uses the RCX infrared frame format at 2400 baud; see {@link Infrared} for wiring. Motors use the global
 * output commands implemented by Scout firmware. Sensor type, mode and polling commands are shared with the RCX. A
 * receiver is required to read sensors and acknowledgements. Without it, commands leave a short gap before the next
 * transmission.
 */
public final class RemoteScout {
    /** Scout input 1. */
    public static final int INPUT_1 = RcxBrick.INPUT_1;
    /** Scout input 2. */
    public static final int INPUT_2 = RcxBrick.INPUT_2;

    /** Standalone mode: run the stored program. */
    public static final int MODE_STANDALONE = 0;
    /** Power mode: act as a remotely controlled powered brick. */
    public static final int MODE_POWER = 1;

    /** Scout sounds accepted by {@link #playSound(int)}. */
    public static final int SOUND_REMOTE = 6;
    public static final int SOUND_ENTER_STANDALONE = 7;
    public static final int SOUND_KEY_ERROR = 8;
    public static final int SOUND_NONE = 9;
    public static final int SOUND_TOUCH_1_PRESSED = 10;
    public static final int SOUND_TOUCH_1_RELEASED = 11;
    public static final int SOUND_TOUCH_2_PRESSED = 12;
    public static final int SOUND_TOUCH_2_RELEASED = 13;
    public static final int SOUND_ENTER_BRIGHT = 14;
    public static final int SOUND_ENTER_NORMAL = 15;
    public static final int SOUND_ENTER_DARK = 16;
    public static final int SOUND_ONE_BLINK = 17;
    public static final int SOUND_TWO_BLINKS = 18;
    public static final int SOUND_COUNTER_1 = 19;
    public static final int SOUND_COUNTER_2 = 20;
    public static final int SOUND_TIMER_1 = 21;
    public static final int SOUND_TIMER_2 = 22;
    public static final int SOUND_TIMER_3 = 23;
    public static final int SOUND_MAIL_RECEIVED = 24;
    public static final int SOUND_SPECIAL_1 = 25;
    public static final int SOUND_SPECIAL_2 = 26;
    public static final int SOUND_SPECIAL_3 = 27;

    /** Scout sound sets accepted by {@link #selectSoundSet(int)}. */
    public static final int SOUND_SET_NONE = 0;
    public static final int SOUND_SET_BASIC = 1;
    public static final int SOUND_SET_BUG = 2;
    public static final int SOUND_SET_ALARM = 3;
    public static final int SOUND_SET_RANDOM = 4;
    public static final int SOUND_SET_SCIENCE = 5;

    private static final int OPCODE_PING = 0x10;
    private static final int OPCODE_STOP_ALL_TASKS = 0x50;
    private static final int OPCODE_POWER_OFF = 0x60;
    private static final int OPCODE_PLAY_SOUND = 0x51;
    private static final int OPCODE_MUTE_SOUND = 0xD0;
    private static final int OPCODE_UNMUTE_SOUND = 0xE0;
    private static final int OPCODE_SELECT_PROGRAM = 0x91;
    private static final int OPCODE_SCOUT = 0x47;
    private static final int OPCODE_SCOUT_SOUND = 0x57;

    private final RcxLink link;
    private final RcxBrick brick;
    private final ScoutMotor motorA;
    private final ScoutMotor motorB;
    private final RcxSensor sensor1;
    private final RcxSensor sensor2;

    /** A Scout on the board's own infrared link. Create it once, at startup. */
    public RemoteScout() {
        this(new RcxLink());
    }

    /** A Scout on a link shared with other RCX-family classes, so they share one toggle bit. */
    public RemoteScout(RcxLink link) {
        this.link = link;
        this.brick = new RcxBrick(link);
        this.motorA = new ScoutMotor(brick, RcxBrick.OUTPUT_A);
        this.motorB = new ScoutMotor(brick, RcxBrick.OUTPUT_B);
        this.sensor1 = new RcxSensor(brick, INPUT_1);
        this.sensor2 = new RcxSensor(brick, INPUT_2);
    }

    /** Configures the IR LED and optional receiver pins. Use {@code -1} for an unused receiver. */
    public void begin(int receivePin, int transmitPin) {
        link.begin(receivePin, transmitPin);
    }

    /** Scout output A. */
    public ScoutMotor getMotorA() {
        return motorA;
    }

    /** Scout output B. */
    public ScoutMotor getMotorB() {
        return motorB;
    }

    /** Scout input 1. */
    public RcxSensor getSensor1() {
        return sensor1;
    }

    /** Scout input 2. */
    public RcxSensor getSensor2() {
        return sensor2;
    }

    /** Configures one of the Scout's touch inputs; needs an IR receiver to hear acknowledgements. */
    public boolean setTouchSensor(int input) {
        return brick.setTouchSensor(input);
    }

    /** Whether the touch sensor on {@code input} is pressed; false also means no reply arrived. */
    public boolean isPressed(int input) {
        return brick.isPressed(input);
    }

    /** The Scout battery voltage in millivolts, or {@code -1} if it did not answer. */
    public int batteryMillivolts() {
        return brick.batteryMillivolts();
    }

    /** Sends the RCX remote-control button mask, which Scout accepts. */
    public void sendButtons(int buttonMask) {
        link.sendButtons(buttonMask);
    }

    /** Sends an RCX message value, which Scout accepts. */
    public void sendMessage(int value) {
        link.sendMessage(value);
    }

    /** Pings the Scout. */
    public void ping() {
        link.send(OPCODE_PING, 0, 0, 0);
    }

    /** Sets the Scout's {@link #MODE_STANDALONE} or {@link #MODE_POWER} mode. */
    public void setMode(int mode) {
        link.send(OPCODE_SCOUT, mode & 0xFF, 0, 1);
    }

    /** Plays one of the Scout's built-in sounds, {@code SOUND_*} ({@code 6..27}). */
    public void playSound(int sound) {
        link.send(OPCODE_PLAY_SOUND, sound & 0xFF, 0, 1);
    }

    /** Enables Scout sounds and tones. */
    public void unmuteSound() {
        link.send(OPCODE_UNMUTE_SOUND, 0, 0, 0);
    }

    /** Disables Scout sounds and tones. */
    public void muteSound() {
        link.send(OPCODE_MUTE_SOUND, 0, 0, 0);
    }

    /** Selects the sounds used by the Scout's autonomous rules, {@code SOUND_SET_*}. */
    public void selectSoundSet(int soundSet) {
        link.send(OPCODE_SCOUT_SOUND, soundSet & 0xFF, 0, 1);
    }

    /** Selects stored program {@code program}, {@code 1..5}. */
    public void selectProgram(int program) {
        link.send(OPCODE_SELECT_PROGRAM, (program - 1) & 0xFF, 0, 1);
    }

    /** Stops every running task. */
    public void stopAll() {
        link.send(OPCODE_STOP_ALL_TASKS, 0, 0, 0);
    }

    /** Switches the Scout off. */
    public void powerOff() {
        link.send(OPCODE_POWER_OFF, 0, 0, 0);
    }
}
