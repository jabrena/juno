package io.github.jabrena.juno.remote.lego.scout;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.remote.lego.rcx.RcxFrames;
import io.github.jabrena.juno.remote.lego.rcx.RcxLightSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxTemperatureSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LEGO Scout remote")
class RemoteScoutTest {
    private RemoteScout scout;

    @BeforeEach
    void startWithAnEmptyLink() {
        Wire.resetBoard();
        Infrared.reset();
        scout = new RemoteScout(new RcxLink(SimulatedTime.clock()));
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
        scout.setMode(RemoteScout.MODE_POWER);

        // Then
        assertThat(Infrared.written).containsSubsequence(1, 0xFE);
    }

    @Test
    @DisplayName("Play sound carries its argument")
    void shouldSendTheSoundArgument() {
        // When
        scout.playSound(RemoteScout.SOUND_REMOTE);

        // Then
        assertThat(Infrared.written).containsSubsequence(RemoteScout.SOUND_REMOTE, 0xF9);
    }

    @Test
    @DisplayName("Scout sound controls use mute, sound-group and unmute opcodes")
    void shouldControlTheScoutSpeakerAndSoundSet() {
        // When
        scout.unmuteSound();
        scout.selectSoundSet(RemoteScout.SOUND_SET_SCIENCE);
        scout.muteSound();

        // Then
        assertThat(Infrared.written).containsSubsequence(0xE8, 0x17)
                .containsSubsequence(0x57, 0xA8, RemoteScout.SOUND_SET_SCIENCE, 0xFA)
                .containsSubsequence(0xD8, 0x27);
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
    @DisplayName("Scout motors use the Scout-compatible global output commands")
    void shouldDriveScoutMotorWithGlobalCommands() {
        // When
        scout.getMotorA().drive(3);

        // Then
        assertThat(Infrared.written).hasSize(31).containsSubsequence(0xAB, 0x54, 0x01, 0xFE, 0x02, 0xFD, 0x03, 0xFC)
                .containsSubsequence(0x77, 0x88, 0x81, 0x7E)
                .containsSubsequence(0x6F, 0x90, 0x81, 0x7E);
    }

    @Test
    @DisplayName("Scout sensor handles configure and read the selected input")
    void shouldConfigureAndReadAScoutSensor() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x2A, 0x00));

        // When
        RcxLightSensor sensor = new RcxLightSensor(scout.getSensor2());
        boolean configured = sensor.begin();
        int value = sensor.lightPercent();

        // Then
        assertThat(configured).isTrue();
        assertThat(value).isEqualTo(42);
    }

    @Test
    @DisplayName("RCX touch sensor adapter connects to a Scout input")
    void shouldReadTouchSensorThroughTheRcxSensorAdapter() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x01, 0x00));
        RcxTouchSensor sensor = new RcxTouchSensor(scout.getSensor1());

        // When
        boolean configured = sensor.begin();
        boolean pressed = sensor.isPressed();

        // Then
        assertThat(configured).isTrue();
        assertThat(pressed).isTrue();
    }

    @Test
    @DisplayName("RCX temperature sensor adapter connects to a Scout input")
    void shouldReadTemperatureSensorThroughTheRcxSensorAdapter() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0xEB, 0x00));
        RcxTemperatureSensor sensor = new RcxTemperatureSensor(scout.getSensor2());

        // When
        boolean configured = sensor.begin();
        int temperatureTenths = sensor.temperatureTenths();

        // Then
        assertThat(configured).isTrue();
        assertThat(temperatureTenths).isEqualTo(235);
    }

    @Test
    @DisplayName("Setting the touch sensor on the brick succeeds")
    void shouldConfigureTheTouchSensorOnTheBrick() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));

        // When
        boolean configured = scout.setTouchSensor(RemoteScout.INPUT_1);

        // Then
        assertThat(configured).isTrue();
    }

    @Test
    @DisplayName("Touch sensor reports pressed from the brick reply")
    void shouldReportTheTouchSensorPressedFromTheBrickReply() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x01, 0x00));

        // When
        boolean pressed = scout.isPressed(RemoteScout.INPUT_1);

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

}
