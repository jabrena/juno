package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Runs the firmware in {@link H8Simulator} against a {@link FakeRcx} that delivers LEGO direct commands. */
class RcxRemoteFirmwareTest {
    private static final int MAX_STEPS = 200_000;
    private static final String BOOT_BEEP = "sound 1";
    /** Twenty of the downloader's 200-byte blocks: the image downloads in well under a minute. */
    private static final int TWENTY_DOWNLOAD_BLOCKS = 4000;

    private final FakeRcx rcx = new FakeRcx();

    private H8Simulator run() {
        return run(new RcxRemoteFirmware());
    }

    private H8Simulator run(RcxRemoteFirmware firmware) {
        H8Simulator cpu = new H8Simulator(firmware.image(), rcx);
        cpu.run(MAX_STEPS);
        return cpu;
    }

    private List<String> callsAfterBoot() {
        return rcx.calls().subList(rcx.calls().indexOf(BOOT_BEEP) + 1, rcx.calls().size());
    }

    @Test
    void holdsTheUnlockTextAndFitsAFewDownloadBlocks() {
        H8Image image = new RcxRemoteFirmware().image();

        assertThat(image.entry()).isEqualTo(RcxRom.FIRMWARE_START);
        assertThat(image.containsBefore(RcxRom.UNLOCK_TEXT, RcxRom.UNLOCK_LIMIT)).isTrue();
        assertThat(image.bytes().length).isLessThanOrEqualTo(TWENTY_DOWNLOAD_BLOCKS);
        assertThat(image.bytes()).isEqualTo(new RcxRemoteFirmware().image().bytes());
    }

    @Test
    void redrawsIconsOnAClearedDisplayWithoutClearingThemOneByOne() {
        // On the brick, clearing icons one by one and lighting them again every 0.3 s left only the standing figure;
        // a cleared display followed by the lit icons shows them all.
        rcx.receive(0x10);
        rcx.receive(0x21, 0x80 | 0x1);

        run();

        assertThat(rcx.displayCalls()).noneMatch(call -> call.startsWith("clear 3"));
        boolean cleared = false;
        for (String call : rcx.displayCalls()) {
            if (call.equals("refresh") || call.equals("reply")) {
                cleared = false;
            } else if (call.equals("clear display")) {
                cleared = true;
            } else {
                assertThat(cleared).as("an icon lit without clearing the display first: %s", rcx.displayCalls())
                        .isTrue();
            }
        }
        assertThat(rcx.icons()).containsExactlyInAnyOrder(RcxRom.LCD_ICONS + 0x07, RcxRom.LCD_ICONS + 0x10);
        assertThat(rcx.displayed()).isEqualTo(-1);
    }

    @Test
    void repliesOnlyOnceTheDisplayIsRedrawn() {
        // The Arduino sends its next command as soon as the reply arrives; on the brick, a command that came while the
        // display was still being redrawn after the reply was lost.
        rcx.receive(0xB2, 0x0E, 1);

        run();

        List<String> calls = rcx.displayCalls();
        assertThat(calls.subList(calls.size() - 2, calls.size())).containsExactly("refresh", "reply");
        assertThat(rcx.icons()).contains(RcxRom.LCD_ICONS + 0x0E);
    }

    @Test
    void showsJunoWhenTheFirmwareStarts() {
        H8Simulator cpu = run();

        assertThat(new int[] {
                    cpu.readByte(RcxRom.LCD_DISPLAY_MEMORY), cpu.readByte(RcxRom.LCD_DISPLAY_MEMORY + 1),
                    cpu.readByte(RcxRom.LCD_DISPLAY_MEMORY + 2), cpu.readByte(RcxRom.LCD_DISPLAY_MEMORY + 3),
                    cpu.readByte(RcxRom.LCD_DISPLAY_MEMORY + 4), cpu.readByte(RcxRom.LCD_DISPLAY_MEMORY + 5),
                    cpu.readByte(RcxRom.LCD_DISPLAY_MEMORY + 6), cpu.readByte(RcxRom.LCD_DISPLAY_MEMORY + 7),
                    cpu.readByte(RcxRom.LCD_DISPLAY_MEMORY + 8)
                })
                .containsExactly(0xA0, 0x02, 0x00, 0x88, 0x08, 0x88, 0x80, 0x8A, 0xAA);
    }

    @Test
    void answersAliveWithTheComplementedOpcode() {
        rcx.receive(0x10);
        rcx.receive(0x18);

        run();

        assertThat(rcx.replies()).containsExactly(new int[] {0xEF}, new int[] {0xE7});
        assertThat(rcx.displayed()).as("JUno stays until a number is set").isEqualTo(-1);
    }

    @Test
    void reportsItsVersionsSoDownloadersSeeFirmwareInstalled() {
        rcx.receive(0x15, 1, 3, 5, 7, 11);

        run();

        assertThat(rcx.replies()).containsExactly(new int[] {0xEA, 0, 3, 0, 1, 0, 0, 0, 1});
    }

    @Test
    void turnsSelectedOutputsOnForwardAtFullPowerByDefault() {
        rcx.receive(0x21, 0x80 | 0x1 | 0x4);

        run();

        assertThat(callsAfterBoot()).containsExactly("motor 2000 1 7", "motor 2001 4 7", "motor 2002 1 7");
        assertThat(rcx.replies()).containsExactly(new int[] {0xDE});
    }

    @Test
    void appliesDirectionPowerOffAndFloat() {
        rcx.receive(0xE1, 0x00 | 0x4);
        rcx.receive(0x13, 0x1, 2, 3);
        rcx.receive(0x29, 0x80 | 0x1 | 0x4);
        rcx.receive(0xE9, 0x40 | 0x1);
        rcx.receive(0x21, 0x40 | 0x1);
        rcx.receive(0x29, 0x00 | 0x4);

        run();

        List<String> calls = callsAfterBoot();
        assertThat(calls.subList(calls.size() - 12, calls.size())).containsExactly(
                "motor 2000 1 3", "motor 2001 4 7", "motor 2002 2 7",
                "motor 2000 2 3", "motor 2001 4 7", "motor 2002 2 7",
                "motor 2000 3 3", "motor 2001 4 7", "motor 2002 2 7",
                "motor 2000 3 3", "motor 2001 4 7", "motor 2002 4 7");
        assertThat(rcx.replies()).hasSize(6);
    }

    @Test
    void ignoresPowerFromSourcesOtherThanAConstant() {
        rcx.receive(0x13, 0x1, 0, 3);
        rcx.receive(0x21, 0x80 | 0x1);

        run();

        assertThat(callsAfterBoot()).endsWith("motor 2000 1 7", "motor 2001 4 7", "motor 2002 4 7");
    }

    @Test
    void playsSystemSoundsAndTones() {
        rcx.receive(0x51, 2);
        rcx.receive(0x23, 440 & 0xFF, 440 >> 8, 50);

        run();

        assertThat(callsAfterBoot()).containsExactly("sound 2", "tone 440 50");
        assertThat(rcx.replies()).containsExactly(new int[] {0xAE}, new int[] {0xDC});
    }

    @Test
    void reportsTheBatteryInMillivolts() {
        rcx.batteryReading(287);
        rcx.receive(0x30);

        run();

        int millivolts = 287 * 28 + 287 / 5;
        assertThat(rcx.replies()).containsExactly(new int[] {0xCF, millivolts & 0xFF, millivolts >> 8});
        assertThat(millivolts).isCloseTo(287 * 43988 / 1560, within(10));
    }

    @Test
    void leavesUnknownMessagesUnanswered() {
        rcx.receive(0xF7, 1);
        rcx.receive(0x10);

        run();

        assertThat(rcx.replies()).containsExactly(new int[] {0xEF});
        assertThat(rcx.displayed()).isEqualTo(-1);
    }

    @Test
    void setsTypesWithLegoDefaultModesAndPowersOnlyActiveSensors() {
        rcx.receive(0x32, 0, 2);
        rcx.receive(0x32, 1, 1);
        rcx.receive(0x3A, 2, 3);
        rcx.receive(0x12, 11, 0);
        rcx.receive(0x12, 11, 1);
        rcx.receive(0x12, 11, 2);
        rcx.receive(0x12, 10, 2);

        run();

        assertThat(callsAfterBoot()).filteredOn(call -> call.startsWith("active") || call.startsWith("passive"))
                .containsExactly("passive 1000", "passive 1001", "active 1002");
        assertThat(rcx.replies()).containsExactly(new int[] {0xCD}, new int[] {0xCD}, new int[] {0xC5},
                new int[] {0xED, 0xA0, 0}, new int[] {0xED, 0x20, 0}, new int[] {0xED, 0x80, 0},
                new int[] {0xED, 3, 0});
    }

    @Test
    void pollsTheValueRawReadingAndBoolean() {
        rcx.sensorReading(2, 700, 55, 0);
        rcx.receive(0x12, 9, 2);
        rcx.receive(0x12, 12, 2);
        rcx.receive(0x12, 13, 2);

        run();

        assertThat(rcx.replies()).containsExactly(new int[] {0xED, 55, 0}, new int[] {0xED, 700 & 0xFF, 700 >> 8},
                new int[] {0xED, 1, 0});
    }

    @Test
    void keepsCountingRotationUntilCleared() {
        rcx.sensorReading(0, 0, 0, 3);
        rcx.receive(0x32, 0, 4);
        rcx.receive(0x12, 9, 0);
        rcx.receive(0x1A, 9, 0);
        rcx.receive(0xD1, 0);
        rcx.receive(0x12, 9, 0);

        run();

        assertThat(callsAfterBoot()).contains("active 1000");
        assertThat(rcx.replies()).containsExactly(new int[] {0xCD}, new int[] {0xED, 3, 0}, new int[] {0xE5, 6, 0},
                new int[] {0x2E}, new int[] {0xED, 3, 0});
    }

    @Test
    void setsModesAndAnswersOtherSourcesAndInputsWithZero() {
        rcx.receive(0x42, 1, 0x80);
        rcx.receive(0x12, 11, 1);
        rcx.receive(0x12, 0, 1);
        rcx.receive(0x12, 9, 5);
        rcx.receive(0x32, 5, 1);

        run();

        assertThat(rcx.replies()).containsExactly(new int[] {0xBD}, new int[] {0xED, 0x80, 0},
                new int[] {0xED, 0, 0}, new int[] {0xED, 0, 0}, new int[] {0xCD});
    }

    @Test
    void readsEveryInputWhenTheAnalogHandlerFlagsFreshReadings() {
        rcx.analogReady();
        rcx.receive(0x10);

        H8Simulator cpu = run();

        assertThat(callsAfterBoot()).containsSubsequence("read 1000", "read 1001", "read 1002");
        assertThat(cpu.readByte(RcxRemoteFirmware.DISPATCH)).isZero();
    }

    @Test
    void sleepsWithOutputsFloatingOnceTheIdleCountdownRunsOut() {
        rcx.idleElapses();
        rcx.receive(0x21, 0x80 | 0x1);

        run();

        assertThat(callsAfterBoot()).containsSubsequence("motor 2000 1 7", "motor 2000 4 7", "sound 0", "sleep");
    }

    @Test
    void wakingRunsTheWholeColdStartAgain() {
        rcx.idleElapses();
        rcx.wakeAfterSleep();
        rcx.receive(0x21, 0x80 | 0x1);
        H8Simulator cpu = new H8Simulator(new RcxRemoteFirmware().image(), rcx);

        cpu.runFor(MAX_STEPS);

        assertThat(callsAfterBoot()).containsSubsequence("motor 2000 1 7", "sleep", "clear memory", BOOT_BEEP);
    }

    @Test
    void sleepsWithAClearDisplayOnceTheRomHandlersAreStopped() {
        // The ROM's handlers draw some icons by themselves: clearing before stopping them left stray segments.
        rcx.idleElapses();
        rcx.receive(0xB2, 0x0E, 1);

        run();

        List<String> calls = rcx.displayCalls();
        assertThat(calls.subList(calls.size() - 3, calls.size())).containsExactly("timer off", "clear display", "refresh");
        assertThat(rcx.icons()).isEmpty();
        assertThat(rcx.calls()).endsWith("sleep");
    }

    @Test
    void aShortOnOffPressSleepsWithoutDeletingTheFirmware() {
        rcx.pressPowerButton();

        H8Simulator cpu = run();

        assertThat(callsAfterBoot()).containsSubsequence("motor 2000 4 7", "motor 2001 4 7", "motor 2002 4 7", "sleep");
        assertThat(cpu.readByte(RcxRom.DELETE_FIRMWARE_FLAG)).isZero();
        assertThat(rcx.calls()).doesNotContain("reset");
    }

    @Test
    void ignoresOnOffContactBounce() {
        rcx.bounceOnOff();
        H8Simulator cpu = new H8Simulator(new RcxRemoteFirmware().image(), rcx);

        cpu.runFor(MAX_STEPS);

        assertThat(rcx.calls()).doesNotContain("sleep", "reset");
    }

    @Test
    void aTwoSecondOnOffHoldReturnsToTheBootLoader() {
        rcx.holdPowerButton();

        H8Simulator cpu = run();

        assertThat(callsAfterBoot()).containsSubsequence("motor 2000 4 7", "motor 2001 4 7", "motor 2002 4 7", "reset");
        assertThat(cpu.readByte(RcxRom.DELETE_FIRMWARE_FLAG)).isEqualTo(1);
        assertThat(rcx.calls()).doesNotContain("sleep");
    }

    @Test
    void restartsTheIdleCountdownOnEveryCommand() {
        rcx.countdownAtMessage(1);
        rcx.receive(0x10);

        H8Simulator cpu = run(new RcxRemoteFirmware(2));

        assertThat(cpu.readWord(RcxRemoteFirmware.SHUTOFF_MINUTES)).isEqualTo(2);
        assertThat(rcx.calls()).doesNotContain("sleep");
    }

    @Test
    void rejectsAnAutoOffTimeBelowOneMinute() {
        assertThatThrownBy(() -> new RcxRemoteFirmware(0)).isInstanceOf(FirmwareGenerationException.class);
    }

    @Test
    void showsASignedNumberInPlaceOfJuno() {
        rcx.receive(0x10);
        rcx.receive(0x72, -42 & 0xFF, (-42 >> 8) & 0xFF);
        rcx.receive(0x18);

        run();

        assertThat(rcx.replies()).containsExactly(new int[] {0xEF}, new int[] {0x8D}, new int[] {0xE7});
        assertThat(rcx.displayed()).isEqualTo(-42 & 0xFFFF);
        assertThat(rcx.displayStyle()).isEqualTo(RcxRom.LCD_SIGNED);
    }

    @Test
    void lightsAndHandsBackIconsOnRequest() {
        rcx.receive(0xB2, 0x18, 1);
        rcx.receive(0xBA, 0x1C, 1);
        rcx.receive(0xB2, 0x18, 0);

        run();

        assertThat(rcx.icons()).contains(0x301C).doesNotContain(0x3018);
        assertThat(rcx.everLit()).contains(0x3018);
        assertThat(rcx.replies()).containsExactly(new int[] {0x4D}, new int[] {0x45}, new int[] {0x4D});
    }

    @Test
    void lightsEverySegmentUntilAskedToStop() {
        rcx.receive(0xB2, 0x20, 1);
        rcx.receive(0xBA, 0x20, 0);

        run();

        assertThat(rcx.everLit()).contains(0x3020);
        assertThat(rcx.icons()).doesNotContain(0x3020);
    }

    @Test
    void acknowledgesButIgnoresIconsOutOfRange() {
        rcx.receive(0xB2, 0x05, 1);
        rcx.receive(0xBA, 0x21, 1);

        run();

        assertThat(rcx.replies()).containsExactly(new int[] {0x4D}, new int[] {0x45});
        assertThat(rcx.everLit()).doesNotContain(0x3005, 0x3021);
    }

    @Test
    void showsTheArrowOfEveryRunningOutputInItsDirection() {
        rcx.receive(0xE1, 0x00 | 0x4);
        rcx.receive(0x21, 0x80 | 0x1 | 0x4);

        run();

        assertThat(rcx.icons()).contains(0x3010, 0x3015).doesNotContain(0x300F, 0x3013, 0x3016);
    }

    @Test
    void darkensTheArrowWhenTheOutputStops() {
        rcx.receive(0x21, 0x80 | 0x1);
        rcx.receive(0x29, 0x40 | 0x1);

        run();

        assertThat(rcx.everLit()).contains(0x3010);
        assertThat(rcx.icons()).doesNotContain(0x3010);
    }

    @Test
    void standsStillUntilAMotorRunsThenWalks() {
        rcx.idlePolls(20);
        rcx.receive(0x10);

        run();

        assertThat(rcx.icons()).contains(0x3006);
        assertThat(rcx.everLit()).doesNotContain(0x3007);
    }

    @Test
    void walksWhileAMotorRuns() {
        rcx.idlePolls(20);
        rcx.receive(0x21, 0x80 | 0x2);

        run();

        assertThat(rcx.everLit()).contains(0x3006, 0x3007, 0x3013);
    }

    @Test
    void marksAPressedTouchSensor() {
        rcx.sensorReading(1, 100, 1, 0);
        rcx.receive(0x32, 1, 1);
        rcx.receive(0x12, 9, 1);

        run();

        assertThat(rcx.icons()).contains(0x300B).doesNotContain(0x3009, 0x300D);
    }

    @Test
    void warnsOfALowBattery() {
        rcx.batteryReading(200);
        rcx.receive(0x10);

        run();

        assertThat(rcx.icons()).contains(0x301B);
    }

    @Test
    void showsNoBatteryWarningAboveSixAndAHalfVolts() {
        rcx.batteryReading(287);
        rcx.receive(0x10);

        run();

        assertThat(rcx.icons()).doesNotContain(0x301B);
    }

    @Test
    void leavesTheIconsOutWhenAskedTo() {
        rcx.idlePolls(20);
        rcx.receive(0xB2, 0x18, 1);
        rcx.receive(0x21, 0x80 | 0x1);

        run(new RcxRemoteFirmware(RcxRemoteFirmware.DEFAULT_AUTO_OFF_MINUTES, false));

        assertThat(rcx.everLit()).isEmpty();
        assertThat(rcx.replies()).containsExactly(new int[] {0xDE});
        assertThat(new RcxRemoteFirmware(RcxRemoteFirmware.DEFAULT_AUTO_OFF_MINUTES, false).image().bytes().length)
                .isLessThan(new RcxRemoteFirmware().image().bytes().length);
    }

    @Test
    void autoPowerOffAcceptsOneToTheMaximumMinutesOnly() {
        assertThatThrownBy(() -> new RcxRemoteFirmware(0))
                .isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("1 to 32767");
        assertThatThrownBy(() -> new RcxRemoteFirmware(0x8000))
                .isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("1 to 32767");
        assertThat(new RcxRemoteFirmware(1, false).image().bytes()).isNotEmpty();
        assertThat(new RcxRemoteFirmware(0x7FFF).image().bytes()).isNotEmpty();
    }
}
