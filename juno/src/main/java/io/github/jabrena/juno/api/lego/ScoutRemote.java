package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.io.ir.Infrared;

/**
 * Remote control of a LEGO Scout brick (Robotics Discovery Set) over infrared, plain Java on top of
 * {@link Infrared}: see there for the wiring of the IR receiver module and the IR LED. The Scout
 * speaks the RCX infrared framing and opcode set (the NXC and NQC toolchains treat them as one
 * family, with the Scout adding the opcodes {@code 0x47} and {@code 0xD5}), so it understands the
 * remote-control and message commands of {@link RcxRemote}, and also these direct commands.
 *
 * <p>The Scout answers each direct command with an acknowledgement, which this class does not wait
 * for. The opcode values come from the NXC constants; the argument encodings are those of the
 * matching RCX opcodes and, like the Scout mode argument, have not been verified on a Scout.
 *
 * <p>Sending is blocking, about 50 ms per command at 2400 baud. Works on both boards.
 */
public final class ScoutRemote {
    private ScoutRemote() {
    }

    /** {@link #setMode} values: run the stored program on its own, or act as a plain powered brick. */
    public static final int MODE_STANDALONE = 0;
    public static final int MODE_POWER = 1;

    private static final int OPCODE_PING = 0x10;
    private static final int OPCODE_STOP_ALL_TASKS = 0x50;
    private static final int OPCODE_POWER_OFF = 0x60;
    private static final int OPCODE_PLAY_SOUND = 0x51;
    private static final int OPCODE_SELECT_PROGRAM = 0x91;
    private static final int OPCODE_SCOUT = 0x47;

    /** Configures the transmit pin for an IR LED; the receive pin may be {@code -1}. */
    public static void begin(int receivePin, int transmitPin) {
        RcxLink.begin(receivePin, transmitPin);
    }

    /** Sends the same remote-control command as {@link RcxRemote#sendButtons}, which a Scout accepts. */
    public static void sendButtons(int buttonMask) {
        RcxRemote.sendButtons(buttonMask);
    }

    /** Sends the same message command as {@link RcxRemote#sendMessage}, which a Scout accepts. */
    public static void sendMessage(int value) {
        RcxRemote.sendMessage(value);
    }

    /** The Scout's two sensor inputs, numbered 1 and 2 on the brick. */
    public static final int INPUT_1 = RcxBrick.INPUT_1;
    public static final int INPUT_2 = RcxBrick.INPUT_2;

    /**
     * Configures {@code input} for a touch sensor, as {@link RcxBrick#setTouchSensor} does for an RCX (the
     * Scout takes the same commands, which has not been verified), so {@link #isPressed} can read it back. Needs
     * the IR receiver module wired too, since the Scout answers over infrared.
     */
    public static boolean setTouchSensor(int input) {
        return RcxBrick.setTouchSensor(input);
    }

    /** Whether the touch sensor on {@code input} is pressed; {@code false} also when the Scout did not answer. */
    public static boolean isPressed(int input) {
        return RcxBrick.isPressed(input);
    }

    /**
     * The Scout's battery voltage in millivolts, or {@code -1} if it did not answer, as {@link RcxBrick#batteryMillivolts}
     * reads an RCX's (the Scout takes the same command, which has not been verified). Needs the IR receiver wired.
     */
    public static int batteryMillivolts() {
        return RcxBrick.batteryMillivolts();
    }

    /**
     * Plays a tone of {@code frequencyHz} for {@code durationCentiseconds} hundredths of a second ({@code 1..255}), as
     * {@link RcxBrick#playTone} does, and returns whether the Scout acknowledged it, which needs the IR receiver wired.
     * A new tone replaces one still playing, so wait for the duration before sending the next.
     */
    public static boolean playTone(int frequencyHz, int durationCentiseconds) {
        return RcxBrick.playTone(frequencyHz, durationCentiseconds);
    }

    /** Pings the Scout, which makes it acknowledge. */
    public static void ping() {
        RcxLink.send(OPCODE_PING, 0, 0, 0);
    }

    /** Sets the Scout's {@link #MODE_STANDALONE} or {@link #MODE_POWER} mode. */
    public static void setMode(int mode) {
        RcxLink.send(OPCODE_SCOUT, mode & 0xFF, 0, 1);
    }

    /** Plays the built-in sound {@code sound}, {@code 0..5}. */
    public static void playSound(int sound) {
        RcxLink.send(OPCODE_PLAY_SOUND, sound & 0xFF, 0, 1);
    }

    /** Selects the stored program {@code program}, {@code 1..5}. */
    public static void selectProgram(int program) {
        RcxLink.send(OPCODE_SELECT_PROGRAM, (program - 1) & 0xFF, 0, 1);
    }

    /** Stops every running task. */
    public static void stopAll() {
        RcxLink.send(OPCODE_STOP_ALL_TASKS, 0, 0, 0);
    }

    /** Switches the Scout off. */
    public static void powerOff() {
        RcxLink.send(OPCODE_POWER_OFF, 0, 0, 0);
    }
}
