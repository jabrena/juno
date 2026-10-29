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
 * <tr><td>0x12</td><td>poll input 0-2: value (source 9), type (10), mode (11), raw (12), boolean (13); the
 * last message (source 15); or, Juno's own, the brick's buttons clicked since the last such poll (source 0x60: Run
 * 0x01, View 0x02, Prgm 0x04)</td><td>the value (low, high); 0 for other sources</td></tr>
 * <tr><td>0x51</td><td>play system sound 0-5</td><td>none</td></tr>
 * <tr><td>0x23</td><td>play tone: frequency in Hz (low, high), duration</td><td>none</td></tr>
 * <tr><td>0x92</td><td>select the tone-activity lamp output (channel 1-5, output A-C); applied only to the selected channel</td>
 * <td>none</td></tr>
 * <tr><td>0x30</td><td>battery level</td><td>millivolts (low, high)</td></tr>
 * <tr><td>0x72</td><td>show a signed number (low, high) instead of {@code JUno}; Juno's own, unused by
 * LEGO's firmware</td><td>none</td></tr>
 * <tr><td>0xB2</td><td>display icon 0x06-0x20 ({@link RcxRom#LCD_ICONS}), then 1 lit or 0 left to the brick; Juno's
 * own</td><td>none</td></tr>
 * <tr><td>0x82</td><td>show text: position 0-3, left to right, then an ASCII character; any other position blanks
 * all four. The display then shows the text and only the IR cones until a number is shown. Juno's own</td>
 * <td>none</td></tr>
 * <tr><td>0xD2</td><td>the RCX Remote Control's buttons (high, low): motor buttons run their output while held,
 * message buttons set the message to 1-3, stop floats every output, sound beeps; program buttons are ignored</td>
 * <td>no reply, as the remote expects none</td></tr>
 * <tr><td>0xF7</td><td>set the message (0-255)</td><td>no reply</td></tr>
 * </table>
 *
 * <p>Other messages get no reply. The brick's View and Run clicks change nothing on it; Prgm advances the selected
 * channel from 1 through 5 and wraps back to 1. The tone-activity lamp output is independently configurable per
 * selected channel and defaults to A after startup. All three clicks are also kept for the poll of source 0x60 (see
 * {@code RcxBrickButtons}). The display shows the selected channel until a number or text is shown (see
 * {@code RcxDisplayText}), and lights its icons from the brick's state as well as on request (see
 * {@code RcxDisplayIcons}):
 * running motors' arrows, pressed touch sensors, low battery, and the minifigure, walking while a motor runs. Outputs
 * start floating, forward, at full power, as LEGO's firmware leaves them. After
 * {@link #DEFAULT_AUTO_OFF_MINUTES} minutes without a message (as LEGO's firmware; remote buttons and messages count
 * too), it floats the outputs, stops powering the sensors, blanks the whole display, icons included, and puts the
 * brick to sleep; On-Off wakes it without losing the firmware, starting it afresh as after the download. A normal
 * On-Off press
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
    private static final int REPLY_LENGTH = 0xC048;
    /** The number 0x72 shows, and whether one was shown (non-zero) or the display shows the selected channel (zero). */
    private static final int SHOWN_NUMBER = 0xC05C;
    private static final int NUMBER_SHOWN = 0xC05E;
    /** Non-zero while the configured tone indicator is lit. */
    static final int TONE_INDICATOR = 0xC0FE;
    /** Selected motor output for the tone indicator: A, B or C, encoded as 0, 1 or 2. */
    static final int TONE_OUTPUT = 0xC0FF;
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
    /**
     * Juno's own display-icon command. It was 0xA2, but on the brick a frame whose opcode byte is exactly 0xA2 (the
     * toggle bit clear) never reached the firmware, while 0xAA and other free opcodes worked with either toggle.
     */
    private static final int OPCODE_SET_ICON = 0xB2;
    /**
     * Juno's own: one character of text. On the brick, 0x82 arrives with either toggle bit, and only with its two
     * arguments: the ROM takes an opcode's low three bits as its argument count.
     */
    private static final int OPCODE_SET_CHARACTER = 0x82;
    /** The RCX Remote Control's button state. */
    private static final int OPCODE_REMOTE_BUTTONS = 0xD2;
    private static final int OPCODE_SET_MESSAGE = 0xF7;
    /** Juno's own: selects the tone-indicator output for the currently selected channel. */
    private static final int OPCODE_SET_TONE_OUTPUT = 0x92;
    /** Juno's own: selects which brick accepts the following ordinary RCX command. */
    private static final int OPCODE_SELECT_CHANNEL = 0x91;
    /** Tenths of a second between icon redraws, the walking figure's step. */
    private static final int ICON_TICK_TENTHS = 3;
    /** Holding On-Off this long deliberately returns the brick to its ROM downloader. */
    private static final int DELETE_HOLD_TENTHS = 20;
    /** On-Off must read released this long after a wake-up before a press counts, so release bounce is ignored. */
    private static final int SETTLE_TENTHS = 3;
    /** The command-line flag that leaves the display icons out. */
    private static final String NO_ICONS = "--no-icons";
    private static final String DRAW_BRAND = "draw_brand";
    private static final Path SRECORD_OUTPUT =
            Path.of("juno-third-parties/juno-rcx-firmware/src/main/resources/rcx/firmware.srec");
    private static final Path JAVA_OUTPUT = Path.of(
            "juno-third-parties/juno-rcx-firmware/src/main/java/io/github/jabrena/juno/thirdparty/lego/rcxfirmware/RcxFirmwareImage.java");
    /** The versions reply: ROM 0003.0001 as LEGO's ROM reports it, then firmware 0000.0001. */
    private static final int[] VERSIONS = {0x00, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01};
    private static final int SOUND_BEEP_BEEP = 1;
    private static final int SOUND_CLICK = 0;
    private static final int SOUND_VALUES = 0x07;

    private final int autoOffMinutes;
    private final boolean withIcons;
    private final H8Assembler assembler = new H8Assembler(RcxRom.FIRMWARE_START);
    private final RcxCode code = new RcxCode(assembler);
    private final RcxRom rom = code.rom();
    private final RcxMotorCommands motors = new RcxMotorCommands(code);
    private final RcxSensorCommands sensors = new RcxSensorCommands(code);
    private final RcxDisplayIcons icons = new RcxDisplayIcons(code);
    private final RcxRemoteControl remote = new RcxRemoteControl(code, motors);
    private final RcxChannel channel = new RcxChannel(code);
    private final RcxBrickButtons buttons = new RcxBrickButtons(code, channel);
    private final RcxDisplayText text = new RcxDisplayText(code);

    public RcxRemoteFirmware() {
        this(DEFAULT_AUTO_OFF_MINUTES);
    }

    /** A firmware that sleeps after {@code autoOffMinutes} minutes, 1 or more, without a command. */
    public RcxRemoteFirmware(int autoOffMinutes) {
        this(autoOffMinutes, true);
    }

    /**
     * A firmware that sleeps after {@code autoOffMinutes} minutes, 1 or more, without a command, with or without the
     * display icons (command 0xB2 and the icons the brick lights by itself); without them the display shows only the
     * the selected channel or the number command 0x72 set.
     */
    public RcxRemoteFirmware(int autoOffMinutes, boolean withIcons) {
        if (autoOffMinutes < 1 || autoOffMinutes > 0x7FFF) {
            throw new FirmwareGenerationException("Auto power-off needs 1 to 32767 minutes, not " + autoOffMinutes);
        }
        this.autoOffMinutes = autoOffMinutes;
        this.withIcons = withIcons;
    }

    public static void main(String[] args) throws IOException {
        boolean withIcons = !List.of(args).contains(NO_ICONS);
        List<String> rest = List.of(args).stream().filter(arg -> !arg.equals(NO_ICONS)).toList();
        if (rest.size() > 1) {
            System.err.println("usage: RcxRemoteFirmware [auto-off minutes] [" + NO_ICONS + "]");
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
        setToneOutput();
        battery();
        showNumber();
        reply();
        showBrand();
        blankDisplay();
        updateDisplay();
        powerOff();
        motors.handlers();
        sensors.handlers();
        remote.handlers();
        buttons.handlers();
        channel.handlers();
        text.handlers(NUMBER_SHOWN);
        if (withIcons) {
            icons.handlers(motors, sensors, text);
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
        rom.call(RcxRom.INIT_BUTTONS);
        rom.call(RcxRom.INIT_SERIAL, RECEIVED_FLAG, COUNTERS, 1, 1);
        assembler.call(RcxSensorCommands.POWER_ALL);
        restartAutoOff();
        assembler.call("show_brand");
        assembler.call(RcxChannel.START);
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
        assembler.call(RcxBrickButtons.CHECK);
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
        // Every message keeps the brick awake, including those that get no reply, such as the remote's buttons.
        restartAutoOff();
        if (withIcons) {
            icons.signalReceived();
        }
    }

    /** Writes the reply's opcode and jumps to the command's handler. */
    private void dispatch() {
        assembler.loadByte(RcxCode.REQUEST, H8ByteRegister.R0L);
        assembler.move(H8ByteRegister.R0L, H8ByteRegister.R1L);
        assembler.not(H8ByteRegister.R1L);
        assembler.storeByte(H8ByteRegister.R1L, RcxCode.REPLY);
        assembler.and(~OPCODE_TOGGLE & 0xFF, H8ByteRegister.R0L);
        assembler.moveImmediate(0, H8ByteRegister.R0H);
        assembler.loadByte(RcxChannel.ADDRESS + 1, H8ByteRegister.R1L);
        assembler.moveImmediate(0, H8ByteRegister.R1H);
        assembler.compare(H8Register.R1, H8Register.R0);
        code.jumpIf(H8Condition.EQUAL, "play_tone");
        handler(OPCODE_SELECT_CHANNEL, RcxChannel.SELECT);
        filterSelectedCommand();
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
        handler(OPCODE_SET_TONE_OUTPUT, "set_tone_output");
        handler(OPCODE_BATTERY, "battery");
        handler(OPCODE_SHOW_NUMBER, "show_number");
        if (withIcons) {
            handler(OPCODE_SET_ICON, RcxDisplayIcons.SET_ICON);
        }
        handler(OPCODE_SET_CHARACTER, RcxDisplayText.SET_CHARACTER);
        handler(OPCODE_REMOTE_BUTTONS, RcxRemoteControl.BUTTONS);
        handler(OPCODE_SET_MESSAGE, RcxRemoteControl.SET_MESSAGE);
        assembler.jump(RcxCode.LOOP);
    }

    /** Lets legacy commands through, or consumes a selector and accepts or rejects exactly one following command. */
    private void filterSelectedCommand() {
        assembler.loadByte(RcxChannel.SELECTION, H8ByteRegister.R1L);
        assembler.compareImmediate(RcxChannel.NO_SELECTION, H8ByteRegister.R1L);
        String accepted = code.newLabel();
        assembler.branch(H8Condition.EQUAL, accepted);
        assembler.compareImmediate(RcxChannel.ACCEPT_SELECTION, H8ByteRegister.R1L);
        String rejected = code.newLabel();
        assembler.branch(H8Condition.NOT_EQUAL, rejected);
        assembler.moveImmediate(RcxChannel.NO_SELECTION, H8ByteRegister.R1L);
        assembler.storeByte(H8ByteRegister.R1L, RcxChannel.SELECTION);
        assembler.jump(accepted);
        assembler.label(rejected);
        assembler.moveImmediate(RcxChannel.NO_SELECTION, H8ByteRegister.R1L);
        assembler.storeByte(H8ByteRegister.R1L, RcxChannel.SELECTION);
        assembler.jump(RcxCode.LOOP);
        assembler.label(accepted);
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
        if (withIcons) {
            icons.signalTone();
        }
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R1L);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R1H);
        assembler.loadByte(RcxCode.REQUEST + 3, H8ByteRegister.R2L);
        assembler.moveImmediate(0, H8ByteRegister.R2H);
        rom.call(RcxRom.PLAY_SOUND_OR_SET_DATA_POINTER, Argument.constant(RcxRom.SOUND_TONE),
                Argument.register(H8Register.R1), Argument.register(H8Register.R2));
        if (withIcons) {
            motors.toneIndicatorOn(TONE_OUTPUT);
            assembler.loadByte(RcxCode.REQUEST + 3, H8ByteRegister.R6L);
            assembler.storeByte(H8ByteRegister.R6L, TONE_INDICATOR);
        }
        code.replyWith(1);
    }

    /** Stores a tone-indicator output only on the brick whose selected channel matches the command. */
    private void setToneOutput() {
        assembler.label("set_tone_output");
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R0L);
        assembler.moveImmediate(0, H8ByteRegister.R0H);
        assembler.loadByte(RcxChannel.ADDRESS, H8ByteRegister.R1L);
        assembler.moveImmediate(0, H8ByteRegister.R1H);
        assembler.compare(H8Register.R1, H8Register.R0);
        code.jumpIf(H8Condition.NOT_EQUAL, RcxCode.LOOP);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R0L);
        assembler.compareImmediate(3, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.CARRY_CLEAR, RcxCode.LOOP);
        assembler.storeByte(H8ByteRegister.R0L, TONE_OUTPUT);
        assembler.jump(RcxCode.LOOP);
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
        text.hideText();
        code.replyWith(1);
    }

    /**
     * Redraws the display, then sends the reply, retrying while the serial port is still busy, so the brick is ready
     * for the next command once the Arduino hears it; {@link RcxCode#NO_REPLY} only redraws.
     */
    private void reply() {
        String redraw = withIcons ? RcxDisplayIcons.UPDATE : "update_display";
        assembler.label(RcxCode.NO_REPLY);
        assembler.call(redraw);
        assembler.jump(RcxCode.LOOP);

        assembler.label(RcxCode.REPLY_ACK);
        assembler.moveImmediate(1, H8Register.R0);
        assembler.label(RcxCode.SEND_REPLY);
        assembler.store(H8Register.R0, REPLY_LENGTH);
        // Redraw before replying: the Arduino sends its next command as soon as the reply arrives, and on the brick a
        // command that came while the display was still being redrawn after the reply was lost.
        assembler.call(redraw);
        assembler.label("send_retry");
        assembler.load(REPLY_LENGTH, H8Register.R5);
        rom.call(RcxRom.SEND_DATA, Argument.constant(RcxRom.SEND_SHORT), Argument.constant(0),
                Argument.constant(RcxCode.REPLY), Argument.register(H8Register.R5));
        assembler.move(H8ByteRegister.R6L, H8ByteRegister.R6L);
        assembler.branch(H8Condition.NOT_EQUAL, "send_retry");
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

        // Auto power-off and a short On-Off press both end here: the display goes dark at once, icons included.
        assembler.label("power_off");
        assembler.call("blank_display");
        motors.floatAll();
        waitWhileOnOffHeld();
        rom.call(RcxRom.PLAY_SYSTEM_SOUND, RcxRom.SOUND_PLAY, SOUND_CLICK);
        assembler.label("wait_sound");
        code.clearWord(STATUS);
        rom.call(RcxRom.GET_SOUND_PLAYING_FLAG, RcxRom.SOUND_PLAYING, STATUS);
        assembler.load(STATUS, H8Register.R6);
        assembler.branch(H8Condition.NOT_EQUAL, "wait_sound");
        shutDownHandlers();
        rom.call(RcxRom.SHUTDOWN_POWER);
        // Waking re-runs the whole cold start, clearing the ROM's serial and display state too: resuming at
        // power_on with the state left from before the sleep left the brick unstable on hardware.
        assembler.jump("start");

        assembler.label("delete_firmware");
        assembler.call("blank_display");
        motors.floatAll();
        waitWhileOnOffHeld();
        shutDownHandlers();
        assembler.moveImmediate(1, H8ByteRegister.R6L);
        assembler.storeByte(H8ByteRegister.R6L, RcxRom.DELETE_FIRMWARE_FLAG);
        assembler.load(RcxRom.RESET_VECTOR, H8Register.R6);
        assembler.call(H8Register.R6);
        assembler.jump("delete_firmware");
    }

    /**
     * Stops the ROM's serial, sensor and timer handlers, then blanks the display again: the handlers draw some icons by
     * themselves, so clearing only before stopping them left stray segments on the sleeping brick.
     */
    private void shutDownHandlers() {
        rom.call(RcxRom.SHUTDOWN_SERIAL);
        rom.call(RcxRom.SHUTDOWN_SENSORS);
        rom.call(RcxRom.SHUTDOWN_TIMER);
        assembler.call("blank_display");
    }

    /**
     * Subroutine: clears the display, then zeroes every byte of the LCD memory too, since {@code JUno} is written there
     * directly rather than through the ROM's display state, and refreshes the LCD.
     */
    private void blankDisplay() {
        assembler.label("blank_display");
        rom.call(RcxRom.CLEAR_DISPLAY);
        assembler.moveImmediate(0, H8ByteRegister.R6L);
        for (int i = 0; i < RcxDisplayText.LCD_BYTES; i++) {
            assembler.storeByte(H8ByteRegister.R6L, RcxRom.LCD_DISPLAY_MEMORY + i);
        }
        rom.call(RcxRom.REFRESH_DISPLAY);
        assembler.returnFromSubroutine();
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
        assembler.call(DRAW_BRAND);
        rom.call(RcxRom.REFRESH_DISPLAY);
        assembler.returnFromSubroutine();

        assembler.label(DRAW_BRAND);
        text.drawBrand();
        assembler.returnFromSubroutine();
    }

    /**
     * Subroutines: {@link RcxCode#DRAW_NUMBER} draws the number 0x72 set, signed, or else the text 0x82 set, or else
     * the startup brand, then draws the selected channel in the program digit;
     * {@code update_display} draws it and refreshes the LCD.
     */
    private void updateDisplay() {
        assembler.label("update_display");
        assembler.call(RcxCode.DRAW_NUMBER);
        rom.call(RcxRom.REFRESH_DISPLAY);
        assembler.returnFromSubroutine();

        assembler.label(RcxCode.DRAW_NUMBER);
        assembler.load(NUMBER_SHOWN, H8Register.R0);
        code.jumpIf(H8Condition.NOT_EQUAL, "draw_shown_number");
        text.jumpIfTextShown("draw_shown_text");
        assembler.call(DRAW_BRAND);
        assembler.call(RcxChannel.DRAW);
        assembler.returnFromSubroutine();
        assembler.label("draw_shown_text");
        text.drawText();
        assembler.call(RcxChannel.DRAW);
        assembler.returnFromSubroutine();
        assembler.label("draw_shown_number");
        assembler.load(SHOWN_NUMBER, H8Register.R1);
        rom.call(RcxRom.SET_LCD_NUMBER, Argument.constant(RcxRom.LCD_SIGNED), Argument.register(H8Register.R1),
                Argument.constant(RcxRom.LCD_NO_COMMA));
        assembler.call(RcxChannel.DRAW);
        assembler.returnFromSubroutine();
    }

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

    /** Polls On-Off until it is let go: its status is 0 while held down. */
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
