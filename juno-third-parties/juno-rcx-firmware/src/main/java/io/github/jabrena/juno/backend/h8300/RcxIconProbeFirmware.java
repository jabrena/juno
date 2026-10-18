package io.github.jabrena.juno.backend.h8300;

import io.github.jabrena.juno.backend.h8300.RcxRom.Argument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A diagnostic RCX firmware that checks the ROM's display routines on their own, without infrared or buttons: for
 * every display icon in turn it shows the icon's number ({@link RcxRom#LCD_ICONS} plus 6 to 32) and lights the icon
 * with {@code set_lcd_segment} for two seconds, then darkens just that icon with {@code clear_lcd_segment} for two
 * seconds while the number stays, and starts over after icon 32, every segment. Watching it tells which routine
 * misbehaves when {@link RcxRemoteFirmware}'s icons do not show.
 *
 * <p>It answers no infrared and ignores On-Off, so take a battery out to leave it. Its command-line entry point
 * replaces the tracked download image; regenerate {@link RcxRemoteFirmware} afterwards to restore the normal firmware.
 *
 * <pre>
 * java -cp juno-third-parties/juno-rcx-firmware/target/classes io.github.jabrena.juno.backend.h8300.RcxIconProbeFirmware
 * </pre>
 */
public final class RcxIconProbeFirmware {
    private static final int DISPATCH = 0xC000;
    private static final int COUNTERS = 0xC006;
    /** The ROM timer's tenths-of-a-second counter, the second word of the counters. */
    static final int TENTHS = COUNTERS + 2;
    private static final int DATA_END = 0xF000;
    /** Tenths of a second each lit and each darkened step lasts. */
    private static final int STEP_TENTHS = 20;
    /** Every icon the ROM draws one by one, then 0x20, every segment; 0x17, 0x1E and 0x1F are number styles. */
    static final int[] ICONS = {
        0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,
        0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x20};
    private static final String WAIT = "wait_step";

    private static final Path SRECORD_OUTPUT =
            Path.of("juno-third-parties/juno-rcx-firmware/src/main/resources/rcx/firmware.srec");
    private static final Path JAVA_OUTPUT = Path.of(
            "juno-third-parties/juno-rcx-firmware/src/main/java/io/github/jabrena/juno/api/thirdparty/lego/rcxfirmware/RcxFirmwareImage.java");

    private final H8Assembler assembler = new H8Assembler(RcxRom.FIRMWARE_START);
    private final RcxRom rom = new RcxRom(assembler);

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            System.err.println("usage: RcxIconProbeFirmware");
            System.exit(2);
        }
        new RcxIconProbeFirmware().writeArtifacts(SRECORD_OUTPUT, JAVA_OUTPUT);
    }

    /** Generates the S-record and Java image from one assembly result. */
    public void writeArtifacts(Path srecordOutput, Path javaOutput) throws IOException {
        H8Image firmware = image();
        Path absoluteSrecord = srecordOutput.toAbsolutePath();
        Files.createDirectories(
                Objects.requireNonNull(absoluteSrecord.getParent(), "S-record output has no parent directory"));
        firmware.writeSrecords(absoluteSrecord, "juno-rcx-icon-probe");
        new RcxFirmwareImageSource(firmware).write(javaOutput);
    }

    /** Assembles the probe; an instance assembles it once. */
    public H8Image image() {
        startUp();
        assembler.label("cycle");
        for (int icon : ICONS) {
            showAlone(icon);
            darken(icon);
        }
        assembler.jump("cycle");
        waitStep();
        assembler.string(RcxRom.UNLOCK_TEXT);
        return assembler.assemble("start");
    }

    /** Clears the data area and starts the ROM timer, which counts the tenths the steps wait for. */
    private void startUp() {
        assembler.label("start");
        assembler.moveImmediate(DISPATCH, H8Register.R0);
        assembler.moveImmediate(DATA_END, H8Register.R1);
        rom.call(RcxRom.CLEAR_MEMORY);
        rom.call(RcxRom.INIT_TIMER, COUNTERS, DISPATCH);
    }

    /** A clean display with only {@code icon}'s number and the icon itself. */
    private void showAlone(int icon) {
        rom.call(RcxRom.CLEAR_DISPLAY);
        assembler.moveImmediate(icon, H8Register.R1);
        rom.call(RcxRom.SET_LCD_NUMBER, Argument.constant(RcxRom.LCD_UNSIGNED), Argument.register(H8Register.R1),
                Argument.constant(RcxRom.LCD_NO_COMMA));
        rom.call(RcxRom.SET_LCD_SEGMENT, RcxRom.LCD_ICONS + icon);
        rom.call(RcxRom.REFRESH_DISPLAY);
        assembler.call(WAIT);
    }

    /** Darkens only {@code icon}; the number must stay. */
    private void darken(int icon) {
        rom.call(RcxRom.CLEAR_LCD_SEGMENT, RcxRom.LCD_ICONS + icon);
        rom.call(RcxRom.REFRESH_DISPLAY);
        assembler.call(WAIT);
    }

    /** Subroutine: returns once the ROM timer has counted {@link #STEP_TENTHS} tenths. */
    private void waitStep() {
        assembler.label(WAIT);
        assembler.moveImmediate(0, H8Register.R6);
        assembler.store(H8Register.R6, TENTHS);
        assembler.label("wait_tick");
        assembler.moveImmediate(STEP_TENTHS, H8Register.R5);
        assembler.load(TENTHS, H8Register.R6);
        assembler.compare(H8Register.R5, H8Register.R6);
        assembler.branch(H8Condition.CARRY_SET, "wait_tick");
        assembler.returnFromSubroutine();
    }
}
