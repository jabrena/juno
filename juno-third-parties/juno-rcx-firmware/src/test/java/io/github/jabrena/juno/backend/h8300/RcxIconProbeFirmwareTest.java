package io.github.jabrena.juno.backend.h8300;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps the icon probe through {@link H8Simulator}, standing in for the ROM timer by setting its tenths counter. */
@NullMarked
@DisplayName("RCX icon probe firmware")
class RcxIconProbeFirmwareTest {
    /** Enough instructions to reach the next wait and spin in it. */
    private static final int STEPS = 2_000;
    private static final int STEP_TENTHS = 20;

    private final FakeRcx rcx = new FakeRcx();
    private final H8Simulator cpu = new H8Simulator(new RcxIconProbeFirmware().image(), rcx);

    @Test
    @DisplayName("Holds the unlock text before its limit")
    void shouldHoldTheUnlockTextBeforeItsLimit() {
        // Given
        H8Image image = new RcxIconProbeFirmware().image();

        // When & Then
        assertThat(image.entry()).isEqualTo(RcxRom.FIRMWARE_START);
        assertThat(image.containsBefore(RcxRom.UNLOCK_TEXT, RcxRom.UNLOCK_LIMIT)).isTrue();
    }

    @Test
    @DisplayName("Shows each icon with its number, then darkens only that icon")
    void shouldShowEachIconWithItsNumberThenDarkenOnlyThatIcon() {
        // Given
        cpu.runFor(STEPS);
        assertThat(rcx.displayed()).isEqualTo(0x06);
        assertThat(rcx.icons()).containsExactly(RcxRom.LCD_ICONS + 0x06);

        // When
        nextStep();

        // Then
        assertThat(rcx.displayed()).isEqualTo(0x06);
        assertThat(rcx.icons()).isEmpty();

        // When
        nextStep();

        // Then
        assertThat(rcx.displayed()).isEqualTo(0x07);
        assertThat(rcx.icons()).containsExactly(RcxRom.LCD_ICONS + 0x07);
    }

    @Test
    @DisplayName("Lights every segment, ends the cycle and starts over")
    void shouldLightEverySegmentEndTheCycleAndStartOver() {
        // Given
        cpu.runFor(STEPS);

        // When
        for (int step = 1; step < 2 * RcxIconProbeFirmware.ICONS.length; step++) {
            nextStep();
        }

        // Then
        assertThat(rcx.everLit()).hasSize(RcxIconProbeFirmware.ICONS.length).contains(RcxRom.LCD_ICONS + 0x20);

        // When
        nextStep();

        // Then
        assertThat(rcx.displayed()).isEqualTo(0x06);
    }

    /** Lets the current two-second step run out. */
    private void nextStep() {
        cpu.writeWord(RcxIconProbeFirmware.TENTHS, STEP_TENTHS);
        cpu.runFor(STEPS);
    }
}
