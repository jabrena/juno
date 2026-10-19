package io.github.jabrena.juno.api.thirdparty.lego.scout;

import io.github.jabrena.juno.api.testing.Wire;
import io.github.jabrena.juno.api.thirdparty.ir.Infrared;
import io.github.jabrena.juno.api.thirdparty.lego.rcx.RcxFrames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LEGO Scout remote")
class ScoutRemoteTest {

    @BeforeEach
    void startWithAnEmptyLink() {
        Wire.resetBoard();
        Infrared.reset();
        ScoutRemote.begin(-1, 3);
    }

    @Test
    @DisplayName("Ping sends a seven-byte frame")
    void shouldSendAPingAsASevenByteFrame() {
        // When
        ScoutRemote.ping();

        // Then
        assertThat(Infrared.written).hasSize(7);
    }

    @Test
    @DisplayName("Stop-all sends a seven-byte frame")
    void shouldSendStopAllAsASevenByteFrame() {
        // When
        ScoutRemote.stopAll();

        // Then
        assertThat(Infrared.written).hasSize(7);
    }

    @Test
    @DisplayName("Power-off sends a seven-byte frame")
    void shouldSendPowerOffAsASevenByteFrame() {
        // When
        ScoutRemote.powerOff();

        // Then
        assertThat(Infrared.written).hasSize(7);
    }

    @Test
    @DisplayName("Set mode carries its argument")
    void shouldSendTheModeArgument() {
        // When
        ScoutRemote.setMode(ScoutRemote.MODE_POWER);

        // Then
        assertThat(Infrared.written).containsSubsequence(1, 0xFE);
    }

    @Test
    @DisplayName("Play sound carries its argument")
    void shouldSendTheSoundArgument() {
        // When
        ScoutRemote.playSound(3);

        // Then
        assertThat(Infrared.written).containsSubsequence(3, 0xFC);
    }

    @Test
    @DisplayName("Select program sends the program number counted from one")
    void shouldSendProgramNumbersCountedFromOneOnTheWire() {
        // When
        ScoutRemote.selectProgram(2);

        // Then
        assertThat(Infrared.written).as("programs are numbered from one on the wire").containsSubsequence(1, 0xFE);
    }

    @Test
    @DisplayName("Buttons are forwarded to the remote link")
    void shouldForwardButtonsToTheRemoteLink() {
        // When
        ScoutRemote.sendButtons(0x1234);

        // Then
        assertThat(Infrared.written).containsSubsequence(0x12, 0xED, 0x34, 0xCB);
    }

    @Test
    @DisplayName("Messages are forwarded to the remote link")
    void shouldForwardMessagesToTheRemoteLink() {
        // When
        ScoutRemote.sendMessage(0x5A);

        // Then
        assertThat(Infrared.written).containsSubsequence(0x5A, 0xA5);
    }

    @Test
    @DisplayName("Setting the touch sensor on the brick succeeds")
    void shouldConfigureTheTouchSensorOnTheBrick() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));

        // When
        boolean configured = ScoutRemote.setTouchSensor(ScoutRemote.INPUT_1);

        // Then
        assertThat(configured).isTrue();
    }

    @Test
    @DisplayName("Touch sensor reports pressed from the brick reply")
    void shouldReportTheTouchSensorPressedFromTheBrickReply() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x01, 0x00));

        // When
        boolean pressed = ScoutRemote.isPressed(ScoutRemote.INPUT_1);

        // Then
        assertThat(pressed).isTrue();
    }

    @Test
    @DisplayName("Battery voltage is read in millivolts from the brick reply")
    void shouldReadTheBatteryVoltageInMillivolts() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x30, 0x34, 0x12));

        // When
        int millivolts = ScoutRemote.batteryMillivolts();

        // Then
        assertThat(millivolts).isEqualTo(0x1234);
    }

    @Test
    @DisplayName("Playing a tone succeeds when the brick acknowledges it")
    void shouldPlayAToneWhenTheBrickAcknowledgesIt() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x23));

        // When
        boolean played = ScoutRemote.playTone(440, 10);

        // Then
        assertThat(played).isTrue();
    }
}
