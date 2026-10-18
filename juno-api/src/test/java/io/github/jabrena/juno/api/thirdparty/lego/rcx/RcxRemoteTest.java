package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.api.testing.Wire;
import io.github.jabrena.juno.api.thirdparty.ir.Infrared;
import io.github.jabrena.juno.api.util.Delay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RcxRemoteTest {

    @BeforeEach
    void startWithAnIdleReceiver() {
        Wire.resetBoard();
        Infrared.reset();
        RcxRemote.begin(2, 3);
    }

    @Test
    void sendButtonsAndSendMessageFrameTheirArguments() {
        RcxRemote.sendButtons(0x1234);
        assertThat(Infrared.written).containsSubsequence(0x12, 0xED, 0x34, 0xCB);

        Infrared.written.clear();
        RcxRemote.sendMessage(0x5A);
        assertThat(Infrared.written).containsSubsequence(0x5A, 0xA5);
    }

    @Test
    void buttonsAreReportedWhileHeldAndForgottenAfterTheHoldTime() {
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xD2, 0x01, 0x80));

        assertThat(RcxRemote.buttons()).isEqualTo(0x0180);

        Delay.millis(400);
        assertThat(RcxRemote.buttons()).as("released after the hold time").isZero();
    }

    @Test
    void aMessageIsReportedOnceAndThenCleared() {
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xF7, 0x2A));

        assertThat(RcxRemote.message()).isEqualTo(0x2A);
        assertThat(RcxRemote.message()).as("no new message").isEqualTo(-1);
    }

    @Test
    void framesForOtherCommandsLeaveTheRemoteStateAlone() {
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0x11, 0x01));

        assertThat(RcxRemote.buttons()).isZero();
        assertThat(RcxRemote.message()).isEqualTo(-1);
    }
}
