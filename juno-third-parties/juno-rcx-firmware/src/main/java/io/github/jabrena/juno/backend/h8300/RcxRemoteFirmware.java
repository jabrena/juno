package io.github.jabrena.juno.backend.h8300;

import io.github.jabrena.juno.backend.h8300.RcxRom.Argument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * An RCX replacement firmware that answers the LEGO infrared direct commands the {@code RcxBrick} API sends, so a Juno
 * program on an Arduino drives the brick's motors, reads its sensors, plays its sounds and reads its battery with
 * Juno's own firmware on the brick instead of LEGO's. The ROM's serial routines receive each message (checking its
 * header, complements and checksum) and send the reply, whose opcode is the complement of the command's.
 *
 * <table>
 * <caption>The commands it answers (the opcode's toggle bit 0x08 is ignored)</caption>
 * <tr><th>Opcode</th><th>Command</th><th>Reply arguments</th></tr>
 * <tr><td>0x10</td><td>alive</td><td>none</td></tr>
 * <tr><td>0x15</td><td>versions</td><td>ROM 3.1, firmware 0.1, so downloaders see firmware installed</td></tr>
 * <tr><td>0x21</td><td>outputs on (0x80), off (0x40) or float (0x00); low three bits select A, B, C</td>
 * <td>none</td></tr>
 * <tr><td>0xE1</td><td>outputs forward (0x80), backward (0x00) or flipped (0x40)</td><td>none</td></tr>
 * <tr><td>0x13</td><td>outputs' power from a constant (source 2), 0-7</td><td>none</td></tr>
 * <tr><td>0x32</td><td>input 0-2 type: raw, touch, temperature, light, rotation</td><td>none</td></tr>
 * <tr><td>0x42</td><td>input 0-2 mode: LEGO slope and mode bits</td><td>none</td></tr>
 * <tr><td>0xD1</td><td>clear input 0-2's value</td><td>none</td></tr>
 * <tr><td>0x12</td><td>poll input 0-2: value (source 9), type (10), mode (11), raw (12), boolean (13)</td>
 * <td>the value (low, high); 0 for other sources</td></tr>
 * <tr><td>0x51</td><td>play system sound 0-5</td><td>none</td></tr>
 * <tr><td>0x23</td><td>play tone: frequency in Hz (low, high), duration</td><td>none</td></tr>
 * <tr><td>0x30</td><td>battery level</td><td>millivolts (low, high)</td></tr>
 * <tr><td>0x72</td><td>show a signed number (low, high) instead of the command counter; Juno's own, unused by
 * LEGO's firmware</td><td>none</td></tr>
 * <tr><td>0xA2</td><td>display icon 0x06-0x20 ({@link RcxRom#LCD_ICONS}), then 1 lit or 0 left to the brick; Juno's
 * own</td><td>none</td></tr>
 * </table>
 *
 * <p>Other messages get no reply. The display starts with {@code JUno}, then counts the commands answered until a
 * number is shown, and lights its icons from the brick's state as well as on request (see {@code RcxDisplayIcons}):
 * running motors' arrows, pressed touch sensors, low battery, and the minifigure, walking while a motor runs. Outputs
 * start floating, forward, at full power, as LEGO's firmware leaves them. After
 * {@link #DEFAULT_AUTO_OFF_MINUTES} minutes without a command (as LEGO's firmware), it floats the outputs, stops
 * powering the sensors and puts the brick to sleep; On-Off wakes it without losing the firmware,
 * starting it afresh as after the download. A normal On-Off press
 * does the same immediately. Holding On-Off for two seconds instead floats the outputs, deletes the downloaded
 * firmware and returns the brick to its ROM downloader, so another firmware can be installed without removing a
 * battery.
 *
 * <pre>
 * java -cp juno-third-parties/juno-rcx-firmware/target/classes io.github.jabrena.juno.backend.h8300.RcxRemoteFirmware [minutes]
 * </pre>
 *
 * <p>Run from the repository root; it updates both the tracked S-record and its Java representation used by
 * {@code RcxFirmwareDownload}.
 */
public final class RcxRemoteFirmware {
    /** LEGO's firmware also sleeps after 15 minutes without a command. */
    public static final int DEFAULT_AUTO_OFF_MINUTES = 15;
    /** The ROM's dispatch flags, which {@code init_timer} is given; the A/D handler sets the first one. */
    static final int DISPATCH = 0xC000;
    /** The ROM's timer counters, right after the dispatch flags; their first word is the serial receive counter. */
    static final int COUNTERS = 0xC006;
    /** The counters' minutes-to-power-off word, which the ROM's timer counts down once a minute. */
    static final int SHUTOFF_MINUTES = COUNTERS + 0x0C;
    /** The counters' first firmware timer, which the ROM's timer counts up every tenth of a second. */
    static final int TENTHS = COUNTERS + 0x02;

    /** The dispatch flag the A/D handler sets when fresh readings are ready. */
    private static final int ANALOG_READY = DISPATCH;
    /** The dispatch flag the serial receiver sets when a message arrived. */
    private static final int RECEIVED_FLAG = DISPATCH + 4;
    private static final int VALID = 0xC040;
    private static final int RECEIVED_LENGTH = 0xC042;
    private static final int STATUS = 0xC044;
    private static final int COMMANDS = 0xC046;
    private static final int REPLY_LENGTH = 0xC048;
    /** The number 0x72 shows, and whether one was shown (non-zero) or the display counts commands (zero). */
    private static final int SHOWN_NUMBER = 0xC05C;
    private static final int NUMBER_SHOWN = 0xC05E;
    private static final int DATA_END = 0xF000;

    private static final int OPCODE_TOGGLE = 0x08;
    private static final int OPCODE_ALIVE = 0x10;
    private static final int OPCODE_VERSIONS = 0x15;
    private static final int OPCODE_ON_OFF_FLOAT = 0x21;
    private static final int OPCODE_DIRECTION = 0xE1;
    private static final int OPCODE_POWER = 0x13;
    private static final int OPCODE_SET_INPUT_TYPE = 0x32;
    private static final int OPCODE_SET_INPUT_MODE = 0x42;
    private static final int OPCODE_CLEAR_SENSOR = 0xD1;
    private static final int OPCODE_POLL = 0x12;
    private static final int OPCODE_PLAY_SOUND = 0x51;
    private static final int OPCODE_PLAY_TONE = 0x23;
    private static final int OPCODE_BATTERY = 0x30;
    private static final int OPCODE_SHOW_NUMBER = 0x72;
    private static final int OPCODE_SET_ICON = 0xA2;
    /** Tenths of a second between icon redraws, the walking figure's step. */
    private static final int ICON_TICK_TENTHS = 3;
    /** Holding On-Off this long deliberately returns the brick to its ROM downloader. */
    private static final int DELETE_HOLD_TENTHS = 20;
    /** On-Off must read released this long after a wake-up before a press counts, so release bounce is ignored. */
    private static final int SETTLE_TENTHS = 3;
    /** The command-line flag that adds the display icons, which still hang the brick on hardware. */
    private static final String ICONS = "--icons";
    private static final Path SRECORD_OUTPUT =
            Path.of("juno-third-parties/juno-rcx-firmware/src/main/resources/rcx/firmware.srec");
    private static final Path JAVA_OUTPUT = Path.of(
            "juno-third-parties/juno-rcx-firmware/src/main/java/io/github/jabrena/juno/api/lego/rcxfirmware/RcxFirmwareImage.java");
    /** The versions reply: ROM 0003.0001 as LEGO's ROM reports it, then firmware 0000.0001. */
    private static final int[] VERSIONS = {0x00, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01};
    private static final int SOUND_BEEP_BEEP = 1;
    private static final int SOUND_CLICK = 0;
    private static final int SOUND_VALUES = 0x07;
    /** LCD bytes for {@code JUno}, left to right, using the four numeric seven-segment positions. */
    private static final int[] JUNO_DISPLAY = {0xA0, 0x02, 0x00, 0x88, 0x08, 0x88, 0x80, 0x8A, 0xAA};

    private final int autoOffMinutes;
    private final boolean withIcons;
    private final H8Assembler assembler = new H8Assembler(RcxRom.FIRMWARE_START);
    private final RcxCode code = new RcxCode(assembler);
    private final RcxRom rom = code.rom();
    private final RcxMotorCommands motors = new RcxMotorCommands(code);
    private final RcxSensorCommands sensors = new RcxSensorCommands(code);
    private final RcxDisplayIcons icons = new RcxDisplayIcons(code);

    public RcxRemoteFirmware() {
        this(DEFAULT_AUTO_OFF_MINUTES);
    }

    /** A firmware that sleeps after {@code autoOffMinutes} minutes, 1 or more, without a command. */
    public RcxRemoteFirmware(int autoOffMinutes) {
        this(autoOffMinutes, true);
    }

    /**
     * A firmware that sleeps after {@code autoOffMinutes} minutes, 1 or more, without a command, with or without the
     * display icons (command 0xA2 and the icons the brick lights by itself); without them the display shows only the
     * command counter or the number command 0x72 set.
     */
    public RcxRemoteFirmware(int autoOffMinutes, boolean withIcons) {
        if (autoOffMinutes < 1 || autoOffMinutes > 0x7FFF) {
            throw new FirmwareGenerationException("Auto power-off needs 1 to 32767 minutes, not " + autoOffMinutes);
        }
        this.autoOffMinutes = autoOffMinutes;
        this.withIcons = withIcons;
    }

    public static void main(String[] args) throws IOException {
        boolean withIcons = List.of(args).contains(ICONS);
        List<String> rest = List.of(args).stream().filter(arg -> !arg.equals(ICONS)).toList();
        if (rest.size() > 1) {
            System.err.println("usage: RcxRemoteFirmware [auto-off minutes] [" + ICONS + "]");
            System.exit(2);
        }
        int minutes = rest.isEmpty() ? DEFAULT_AUTO_OFF_MINUTES : Integer.parseInt(rest.get(0));
        new RcxRemoteFirmware(minutes, withIcons).writeArtifacts(SRECORD_OUTPUT, JAVA_OUTPUT);
    }

    /** Generates the S-record and Java image from one assembly result, so their bytes cannot diverge. */
    public void writeArtifacts(Path srecordOutput, Path javaOutput) throws IOException {
        H8Image firmware = image();
        Path absoluteSrecord = srecordOutput.toAbsolutePath();
        Files.createDirectories(
                Objects.requireNonNull(absoluteSrecord.getParent(), "S-record output has no parent directory"));
        firmware.writeSrecords(absoluteSrecord, "juno-rcx-remote");
        new RcxFirmwareImageSource(firmware).write(javaOutput);
    }

    /** Assembles the firmware; an instance assembles it once. */
    public H8Image image() {
        startUp();
        powerOn();
        mainLoop();
        dispatch();
        versions();
        playSound();
        playTone();
        battery();
        showNumber();
        reply();
        showBrand();
        updateDisplay();
        powerOff();
        motors.handlers();
        sensors.handlers();
        if (withIcons) {
            icons.handlers(motors, sensors);
        }
        assembler.string(RcxRom.UNLOCK_TEXT);
        return assembler.assemble("start");
    }

    /** Clears the data area and sets the outputs' defaults; runs after the download and every wake-up. */
    private void startUp() {
        assembler.label("start");
        assembler.moveImmediate(DISPATCH, H8Register.R0);
        assembler.moveImmediate(DATA_END, H8Register.R1);
        rom.call(RcxRom.CLEAR_MEMORY);
        assembler.load(RcxRom.RESET_VECTOR, H8Register.R6);
        assembler.store(H8Register.R6, RcxRom.FIRMWARE_RESET_VECTOR);
        motors.setDefaults();
    }

    /** Starts the ROM's handlers in the order LEGO's firmware does, right after {@link #startUp}. */
    private void powerOn() {
        assembler.label("power_on");
        rom.call(RcxRom.INIT_TIMER, COUNTERS, DISPATCH);
        rom.call(RcxRom.INIT_SENSORS);
        rom.call(RcxRom.INIT_POWER);
        rom.call(RcxRom.INIT_SERIAL, RECEIVED_FLAG, COUNTERS, 1, 1);
        assembler.call(RcxSensorCommands.POWER_ALL);
        restartAutoOff();
        assembler.call("show_brand");
        if (withIcons) {
            assembler.call(RcxDisplayIcons.UPDATE);
        }
        rom.call(RcxRom.PLAY_SYSTEM_SOUND, RcxRom.SOUND_PLAY, SOUND_BEEP_BEEP);
        waitUntilOnOffSettles();
    }

    /**
     * Polls On-Off, the auto power-off countdown, the A/D handler's flag, the icon timer and the serial receiver; a
     * message goes to {@link #dispatch}.
     */
    private void mainLoop() {
        assembler.label(RcxCode.LOOP);
        rom.call(RcxRom.GET_POWER_STATUS, RcxRom.POWER_STATUS_ON_OFF, STATUS);
        assembler.load(STATUS, H8Register.R6);
        code.jumpIf(H8Condition.EQUAL, "power_button");
        assembler.load(SHUTOFF_MINUTES, H8Register.R6);
        code.jumpIf(H8Condition.LESS_OR_EQUAL, "power_off");
        String received = code.newLabel();
        assembler.loadByte(ANALOG_READY, H8ByteRegister.R6L);
        assembler.branch(H8Condition.EQUAL, received);
        assembler.moveImmediate(0, H8ByteRegister.R6L);
        assembler.storeByte(H8ByteRegister.R6L, ANALOG_READY);
        assembler.call(RcxSensorCommands.UPDATE_ALL);
        assembler.label(received);
        if (withIcons) {
            String noTick = code.newLabel();
            assembler.moveImmediate(ICON_TICK_TENTHS, H8Register.R5);
            assembler.load(TENTHS, H8Register.R6);
            assembler.compare(H8Register.R5, H8Register.R6);
            assembler.branch(H8Condition.CARRY_SET, noTick);
            code.clearWord(TENTHS);
            assembler.call(RcxDisplayIcons.TICK);
            assembler.label(noTick);
        }
        code.clearWord(VALID);
        rom.call(RcxRom.CHECK_FOR_DATA, VALID, 0);
        assembler.load(VALID, H8Register.R6);
        code.jumpIf(H8Condition.EQUAL, RcxCode.LOOP);
        code.clearWord(RECEIVED_LENGTH);
        rom.call(RcxRom.RECEIVE_DATA, RcxCode.REQUEST, RcxCode.REQUEST_CAPACITY, RECEIVED_LENGTH);
    }

    /** Writes the reply's opcode and jumps to the command's handler. */
    private void dispatch() {
        assembler.loadByte(RcxCode.REQUEST, H8ByteRegister.R0L);
        assembler.move(H8ByteRegister.R0L, H8ByteRegister.R1L);
        assembler.not(H8ByteRegister.R1L);
        assembler.storeByte(H8ByteRegister.R1L, RcxCode.REPLY);
        assembler.and(~OPCODE_TOGGLE & 0xFF, H8ByteRegister.R0L);
        handler(OPCODE_ALIVE, RcxCode.REPLY_ACK);
        handler(OPCODE_VERSIONS, "versions");
        handler(OPCODE_ON_OFF_FLOAT, RcxMotorCommands.ON_OFF_FLOAT);
        handler(OPCODE_DIRECTION, RcxMotorCommands.DIRECTION);
        handler(OPCODE_POWER, RcxMotorCommands.POWER);
        handler(OPCODE_SET_INPUT_TYPE, RcxSensorCommands.SET_TYPE);
        handler(OPCODE_SET_INPUT_MODE, RcxSensorCommands.SET_MODE);
        handler(OPCODE_CLEAR_SENSOR, RcxSensorCommands.CLEAR);
        handler(OPCODE_POLL, RcxSensorCommands.POLL);
        handler(OPCODE_PLAY_SOUND, "play_sound");
        handler(OPCODE_PLAY_TONE, "play_tone");
        handler(OPCODE_BATTERY, "battery");
        handler(OPCODE_SHOW_NUMBER, "show_number");
        if (withIcons) {
            handler(OPCODE_SET_ICON, RcxDisplayIcons.SET_ICON);
        }
        assembler.jump(RcxCode.LOOP);
    }

    private void versions() {
        assembler.label("versions");
        for (int i = 0; i < VERSIONS.length; i++) {
            assembler.moveImmediate(VERSIONS[i], H8ByteRegister.R0L);
            assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + 1 + i);
        }
        code.replyWith(1 + VERSIONS.length);
    }

    private void playSound() {
        assembler.label("play_sound");
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R1L);
        assembler.and(SOUND_VALUES, H8ByteRegister.R1L);
        assembler.moveImmediate(0, H8ByteRegister.R1H);
        rom.call(RcxRom.PLAY_SYSTEM_SOUND, Argument.constant(RcxRom.SOUND_PLAY), Argument.register(H8Register.R1));
        code.replyWith(1);
    }

    /** Queues a tone: the ROM takes the frequency in Hz and the duration as LEGO's command carries them. */
    private void playTone() {
        assembler.label("play_tone");
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R1L);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R1H);
        assembler.loadByte(RcxCode.REQUEST + 3, H8ByteRegister.R2L);
        assembler.moveImmediate(0, H8ByteRegister.R2H);
        rom.call(RcxRom.PLAY_SOUND_OR_SET_DATA_POINTER, Argument.constant(RcxRom.SOUND_TONE),
                Argument.register(H8Register.R1), Argument.register(H8Register.R2));
        code.replyWith(1);
    }

    /**
     * Replies with the battery voltage in millivolts: the ROM's reading times 43988 / 1560, computed as reading x 28
     * plus reading / 5 (0.1% high) with 16-bit adds and one 16-by-8-bit divide, exact enough up to 1279 units
     * (36 V), far above the brick's 9 V.
     */
    private void battery() {
        assembler.label("battery");
        rom.call(RcxRom.GET_POWER_STATUS, RcxRom.POWER_STATUS_BATTERY, STATUS);
        assembler.load(STATUS, H8Register.R0);
        assembler.move(H8Register.R0, H8Register.R1);
        assembler.add(H8Register.R1, H8Register.R1);
        assembler.add(H8Register.R1, H8Register.R1);
        assembler.move(H8Register.R1, H8Register.R2);
        assembler.add(H8Register.R1, H8Register.R1);
        assembler.add(H8Register.R1, H8Register.R2);
        assembler.add(H8Register.R1, H8Register.R1);
        assembler.add(H8Register.R1, H8Register.R2);
        assembler.moveImmediate(5, H8ByteRegister.R3L);
        assembler.divideUnsigned(H8ByteRegister.R3L, H8Register.R0);
        assembler.moveImmediate(0, H8ByteRegister.R0H);
        assembler.add(H8Register.R0, H8Register.R2);
        code.replyWithWord(H8Register.R2);
    }

    /** Keeps the number for {@link #updateDisplay}, which shows it once the reply is sent. */
    private void showNumber() {
        assembler.label("show_number");
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R1L);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R1H);
        assembler.store(H8Register.R1, SHOWN_NUMBER);
        assembler.moveImmediate(1, H8Register.R0);
        assembler.store(H8Register.R0, NUMBER_SHOWN);
        code.replyWith(1);
    }

    /**
     * Sends the reply, retrying while the serial port is still busy, then restarts the auto power-off countdown and
     * counts the command on the display.
     */
    private void reply() {
        assembler.label(RcxCode.REPLY_ACK);
        assembler.moveImmediate(1, H8Register.R0);
        assembler.label(RcxCode.SEND_REPLY);
        assembler.store(H8Register.R0, REPLY_LENGTH);
        assembler.label("send_retry");
        assembler.load(REPLY_LENGTH, H8Register.R5);
        rom.call(RcxRom.SEND_DATA, Argument.constant(RcxRom.SEND_SHORT), Argument.constant(0),
                Argument.constant(RcxCode.REPLY), Argument.register(H8Register.R5));
        assembler.move(H8ByteRegister.R6L, H8ByteRegister.R6L);
        assembler.branch(H8Condition.NOT_EQUAL, "send_retry");
        restartAutoOff();
        assembler.load(COMMANDS, H8Register.R0);
        assembler.adds(1, H8Register.R0);
        assembler.store(H8Register.R0, COMMANDS);
        assembler.call("update_display");
        if (withIcons) {
            assembler.call(RcxDisplayIcons.UPDATE);
        }
        assembler.jump(RcxCode.LOOP);
    }

    /** Floats the outputs, waits for On-Off to be let go, and sleeps until the next press. */
    private void powerOff() {
        assembler.label("power_button");
        code.clearWord(TENTHS);
        assembler.label("power_button_held");
        rom.call(RcxRom.GET_POWER_STATUS, RcxRom.POWER_STATUS_ON_OFF, STATUS);
        assembler.load(STATUS, H8Register.R6);
        assembler.branch(H8Condition.NOT_EQUAL, "power_released");
        assembler.moveImmediate(DELETE_HOLD_TENTHS, H8Register.R5);
        assembler.load(TENTHS, H8Register.R6);
        assembler.compare(H8Register.R5, H8Register.R6);
        assembler.branch(H8Condition.CARRY_SET, "power_button_held");
        assembler.jump("delete_firmware");

        // A press released before the timer ticked once is contact bounce, not a press.
        assembler.label("power_released");
        assembler.load(TENTHS, H8Register.R6);
        code.jumpIf(H8Condition.EQUAL, RcxCode.LOOP);

        assembler.label("power_off");
        motors.floatAll();
        waitWhileOnOffHeld();
        rom.call(RcxRom.PLAY_SYSTEM_SOUND, RcxRom.SOUND_PLAY, SOUND_CLICK);
        assembler.label("wait_sound");
        code.clearWord(STATUS);
        rom.call(RcxRom.GET_SOUND_PLAYING_FLAG, RcxRom.SOUND_PLAYING, STATUS);
        assembler.load(STATUS, H8Register.R6);
        assembler.branch(H8Condition.NOT_EQUAL, "wait_sound");
        rom.call(RcxRom.CLEAR_DISPLAY);
        rom.call(RcxRom.REFRESH_DISPLAY);
        rom.call(RcxRom.SHUTDOWN_SERIAL);
        rom.call(RcxRom.SHUTDOWN_SENSORS);
        rom.call(RcxRom.SHUTDOWN_TIMER);
        rom.call(RcxRom.SHUTDOWN_POWER);
        // Waking re-runs the whole cold start, clearing the ROM's serial and display state too: resuming at
        // power_on with the state left from before the sleep left the brick unstable on hardware.
        assembler.jump("start");

        assembler.label("delete_firmware");
        motors.floatAll();
        waitWhileOnOffHeld();
        rom.call(RcxRom.CLEAR_DISPLAY);
        rom.call(RcxRom.REFRESH_DISPLAY);
        rom.call(RcxRom.SHUTDOWN_SERIAL);
        rom.call(RcxRom.SHUTDOWN_SENSORS);
        rom.call(RcxRom.SHUTDOWN_TIMER);
        assembler.moveImmediate(1, H8ByteRegister.R6L);
        assembler.storeByte(H8ByteRegister.R6L, RcxRom.DELETE_FIRMWARE_FLAG);
        assembler.load(RcxRom.RESET_VECTOR, H8Register.R6);
        assembler.call(H8Register.R6);
        assembler.jump("delete_firmware");
    }

    /** Sets the ROM's power-off countdown back to the full idle time. */
    private void restartAutoOff() {
        assembler.moveImmediate(autoOffMinutes, H8Register.R6);
        assembler.store(H8Register.R6, SHUTOFF_MINUTES);
    }

    /** Subroutine: writes {@code JUno} into the four full seven-segment positions and refreshes the LCD. */
    private void showBrand() {
        assembler.label("show_brand");
        rom.call(RcxRom.CLEAR_DISPLAY);
        for (int i = 0; i < JUNO_DISPLAY.length; i++) {
            assembler.moveImmediate(JUNO_DISPLAY[i], H8ByteRegister.R6L);
            assembler.storeByte(H8ByteRegister.R6L, RcxRom.LCD_DISPLAY_MEMORY + i);
        }
        rom.call(RcxRom.REFRESH_DISPLAY);
        assembler.returnFromSubroutine();
    }

    /** Subroutine: shows the number 0x72 set, signed, or else the command counter, unsigned. */
    private void updateDisplay() {
        String counter = code.newLabel();
        assembler.label("update_display");
        assembler.load(NUMBER_SHOWN, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, counter);
        assembler.load(SHOWN_NUMBER, H8Register.R1);
        rom.call(RcxRom.SET_LCD_NUMBER, Argument.constant(RcxRom.LCD_SIGNED), Argument.register(H8Register.R1),
                Argument.constant(RcxRom.LCD_NO_COMMA));
        rom.call(RcxRom.REFRESH_DISPLAY);
        assembler.returnFromSubroutine();
        assembler.label(counter);
        assembler.load(COMMANDS, H8Register.R1);
        rom.call(RcxRom.SET_LCD_NUMBER, Argument.constant(RcxRom.LCD_UNSIGNED), Argument.register(H8Register.R1),
                Argument.constant(RcxRom.LCD_NO_COMMA));
        rom.call(RcxRom.REFRESH_DISPLAY);
        assembler.returnFromSubroutine();
    }

    /** Polls On-Off until it is let go: its status is 0 while held down. */
    /** Waits until On-Off has read released for {@link #SETTLE_TENTHS} tenths in a row. */
    private void waitUntilOnOffSettles() {
        String restart = code.newLabel();
        String poll = code.newLabel();
        assembler.label(restart);
        code.clearWord(TENTHS);
        assembler.label(poll);
        rom.call(RcxRom.GET_POWER_STATUS, RcxRom.POWER_STATUS_ON_OFF, STATUS);
        assembler.load(STATUS, H8Register.R6);
        assembler.branch(H8Condition.EQUAL, restart);
        assembler.moveImmediate(SETTLE_TENTHS, H8Register.R5);
        assembler.load(TENTHS, H8Register.R6);
        assembler.compare(H8Register.R5, H8Register.R6);
        assembler.branch(H8Condition.CARRY_SET, poll);
    }

    private void waitWhileOnOffHeld() {
        String poll = code.newLabel();
        assembler.label(poll);
        rom.call(RcxRom.GET_POWER_STATUS, RcxRom.POWER_STATUS_ON_OFF, STATUS);
        assembler.load(STATUS, H8Register.R6);
        assembler.branch(H8Condition.EQUAL, poll);
    }

    /** Jumps to {@code target} if r0l holds {@code opcode}. */
    private void handler(int opcode, String target) {
        assembler.compareImmediate(opcode, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.EQUAL, target);
    }
}
