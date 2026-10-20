package io.github.jabrena.juno.thirdparty.lego.rcx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import io.github.jabrena.juno.util.Delay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RCX infrared remote")
class RcxRemoteTest {
    private RcxRemote remote;

    @BeforeEach
    void startWithAnIdleReceiver() {
        Wire.resetBoard();
        Infrared.reset();
        remote = new RcxRemote(new RcxLink(SimulatedTime.clock()), SimulatedTime.clock());
        remote.begin(2, 3);
        // The held buttons are static and survive begin(): script a release so no earlier test's buttons leak in.
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xD2, 0x00, 0x00));
        remote.buttons();
        Infrared.replies.clear();
    }

    @Test
    @DisplayName("Sending buttons frames each byte with its complement")
    void shouldFrameEachButtonByteWithItsComplement() {
        // When
        remote.sendButtons(0x1234);

        // Then
        assertThat(Infrared.written).containsSubsequence(0x12, 0xED, 0x34, 0xCB);
    }

    @Test
    @DisplayName("Sending a message frames it with its complement")
    void shouldFrameAMessageWithItsComplement() {
        // When
        remote.sendMessage(0x5A);

        // Then
        assertThat(Infrared.written).containsSubsequence(0x5A, 0xA5);
    }

    @Test
    @DisplayName("Buttons are reported while held")
    void shouldReportButtonsWhileTheyAreHeld() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xD2, 0x01, 0x80));

        // When
        int buttons = remote.buttons();

        // Then
        assertThat(buttons).isEqualTo(0x0180);
    }

    @Test
    @DisplayName("Buttons are forgotten after the hold time")
    void shouldForgetButtonsAfterTheHoldTime() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xD2, 0x01, 0x80));
        remote.buttons();
        Delay.millis(400);

        // When
        int buttons = remote.buttons();

        // Then
        assertThat(buttons).as("released after the hold time").isZero();
    }

    @Test
    @DisplayName("A message is reported once and then cleared")
    void shouldReportAMessageOnce() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xF7, 0x2A));

        // When
        int message = remote.message();

        // Then
        assertThat(message).isEqualTo(0x2A);
    }

    @Test
    @DisplayName("No new message is reported after the first one is read")
    void shouldReportNoNewMessageAfterTheFirstOneIsRead() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xF7, 0x2A));
        remote.message();

        // When
        int message = remote.message();

        // Then
        assertThat(message).as("no new message").isEqualTo(-1);
    }

    @Test
    @DisplayName("Frames for other commands leave the remote state alone")
    void shouldLeaveTheRemoteStateAloneForFramesOfOtherCommands() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0x11, 0x01));

        // When
        int buttons = remote.buttons();
        int message = remote.message();

        // Then
        assertThat(new int[] {buttons, message}).containsExactly(0, -1);
    }
}
