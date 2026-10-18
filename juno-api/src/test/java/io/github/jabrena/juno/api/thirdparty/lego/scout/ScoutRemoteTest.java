package io.github.jabrena.juno.api.thirdparty.lego.scout;

import io.github.jabrena.juno.api.testing.Wire;
import io.github.jabrena.juno.api.thirdparty.ir.Infrared;
import io.github.jabrena.juno.api.thirdparty.lego.rcx.RcxFrames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoutRemoteTest {

    @BeforeEach
    void startWithAnEmptyLink() {
        Wire.resetBoard();
        Infrared.reset();
        ScoutRemote.begin(-1, 3);
    }

    @Test
    void commandsWithoutArgumentsSendTheirOpcodeAndChecksum() {
        ScoutRemote.ping();
        assertThat(Infrared.written).hasSize(7);
        Infrared.written.clear();

        ScoutRemote.stopAll();
        assertThat(Infrared.written).hasSize(7);
        Infrared.written.clear();

        ScoutRemote.powerOff();
        assertThat(Infrared.written).hasSize(7);
    }

    @Test
    void modeSoundAndProgramCommandsCarryTheirArgument() {
        ScoutRemote.setMode(ScoutRemote.MODE_POWER);
        assertThat(Infrared.written).containsSubsequence(1, 0xFE);

        Infrared.written.clear();
        ScoutRemote.playSound(3);
        assertThat(Infrared.written).containsSubsequence(3, 0xFC);

        Infrared.written.clear();
        ScoutRemote.selectProgram(2);
        assertThat(Infrared.written).as("programs are numbered from one on the wire").containsSubsequence(1, 0xFE);
    }

    @Test
    void buttonsAndMessagesAreForwardedToTheRemoteLink() {
        ScoutRemote.sendButtons(0x1234);
        assertThat(Infrared.written).containsSubsequence(0x12, 0xED, 0x34, 0xCB);

        Infrared.written.clear();
        ScoutRemote.sendMessage(0x5A);
        assertThat(Infrared.written).containsSubsequence(0x5A, 0xA5);
    }

    @Test
    void sensorsAndBatteryQueryTheBrick() {
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));
        assertThat(ScoutRemote.setTouchSensor(ScoutRemote.INPUT_1)).isTrue();

        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x01, 0x00));
        assertThat(ScoutRemote.isPressed(ScoutRemote.INPUT_1)).isTrue();

        Infrared.replies.addAll(RcxFrames.replyTo(0x30, 0x34, 0x12));
        assertThat(ScoutRemote.batteryMillivolts()).isEqualTo(0x1234);

        Infrared.replies.addAll(RcxFrames.replyTo(0x23));
        assertThat(ScoutRemote.playTone(440, 10)).isTrue();
    }
}
