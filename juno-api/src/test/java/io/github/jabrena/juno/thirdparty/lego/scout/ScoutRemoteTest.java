package io.github.jabrena.juno.thirdparty.lego.scout;

import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import io.github.jabrena.juno.thirdparty.ir.Infrared;
import io.github.jabrena.juno.thirdparty.lego.rcx.RcxFrames;
import io.github.jabrena.juno.thirdparty.lego.rcx.RcxLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LEGO Scout remote")
class ScoutRemoteTest {
    private ScoutRemote scout;

    @BeforeEach
    void startWithAnEmptyLink() {
        Wire.resetBoard();
        Infrared.reset();
        scout = new ScoutRemote(new RcxLink(SimulatedTime.clock()));
        scout.begin(-1, 3);
    }

    @Test
    @DisplayName("Ping sends a seven-byte frame")
    void shouldSendAPingAsASevenByteFrame() {
        // When
        scout.ping();

        // Then
        assertThat(Infrared.written).hasSize(7);
    }

    @Test
    @DisplayName("Stop-all sends a seven-byte frame")
    void shouldSendStopAllAsASevenByteFrame() {
        // When
        scout.stopAll();

        // Then
        assertThat(Infrared.written).hasSize(7);
    }

    @Test
    @DisplayName("Power-off sends a seven-byte frame")
    void shouldSendPowerOffAsASevenByteFrame() {
        // When
        scout.powerOff();

        // Then
        assertThat(Infrared.written).hasSize(7);
    }

    @Test
    @DisplayName("Set mode carries its argument")
    void shouldSendTheModeArgument() {
        // When
        scout.setMode(ScoutRemote.MODE_POWER);

        // Then
        assertThat(Infrared.written).containsSubsequence(1, 0xFE);
    }

    @Test
    @DisplayName("Play sound carries its argument")
    void shouldSendTheSoundArgument() {
        // When
        scout.playSound(3);

        // Then
        assertThat(Infrared.written).containsSubsequence(3, 0xFC);
    }

    @Test
    @DisplayName("Select program sends the program number counted from one")
    void shouldSendProgramNumbersCountedFromOneOnTheWire() {
        // When
        scout.selectProgram(2);

        // Then
        assertThat(Infrared.written).as("programs are numbered from one on the wire").containsSubsequence(1, 0xFE);
    }

    @Test
    @DisplayName("Buttons are forwarded to the remote link")
    void shouldForwardButtonsToTheRemoteLink() {
        // When
        scout.sendButtons(0x1234);

        // Then
        assertThat(Infrared.written).containsSubsequence(0x12, 0xED, 0x34, 0xCB);
    }

    @Test
    @DisplayName("Messages are forwarded to the remote link")
    void shouldForwardMessagesToTheRemoteLink() {
        // When
        scout.sendMessage(0x5A);

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
        boolean configured = scout.setTouchSensor(ScoutRemote.INPUT_1);

        // Then
        assertThat(configured).isTrue();
    }

    @Test
    @DisplayName("Touch sensor reports pressed from the brick reply")
    void shouldReportTheTouchSensorPressedFromTheBrickReply() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x01, 0x00));

        // When
        boolean pressed = scout.isPressed(ScoutRemote.INPUT_1);

        // Then
        assertThat(pressed).isTrue();
    }

    @Test
    @DisplayName("Battery voltage is read in millivolts from the brick reply")
    void shouldReadTheBatteryVoltageInMillivolts() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x30, 0x34, 0x12));

        // When
        int millivolts = scout.batteryMillivolts();

        // Then
        assertThat(millivolts).isEqualTo(0x1234);
    }

    @Test
    @DisplayName("Playing a tone succeeds when the brick acknowledges it")
    void shouldPlayAToneWhenTheBrickAcknowledgesIt() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x23));

        // When
        boolean played = scout.playTone(440, 10);

        // Then
        assertThat(played).isTrue();
    }
}
