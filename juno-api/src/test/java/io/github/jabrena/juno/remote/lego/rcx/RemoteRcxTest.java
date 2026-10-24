package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Remote RCX brick")
class RemoteRcxTest {
    private RemoteRcx remote;

    @BeforeEach
    void startWithAnIdleReceiver() {
        Wire.resetBoard();
        Infrared.reset();
        remote = new RemoteRcx(new RcxLink(SimulatedTime.clock()), 2, 3, SimulatedTime.clock());
        remote.begin();
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
        SimulatedTime.now += 400_000;

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

    @Test
    @DisplayName("Each part getter returns the same instance every call")
    void shouldReturnTheSameInstanceFromEachPartGetter() {
        // When & Then
        assertThat(remote.getMotorA()).isSameAs(remote.getMotorA());
        assertThat(remote.getSensor2()).isSameAs(remote.getSensor2());
        assertThat(remote.getLcd()).isSameAs(remote.getLcd());
        assertThat(remote.getAudio()).isSameAs(remote.getAudio());
        assertThat(remote.getBattery()).isSameAs(remote.getBattery());
        assertThat(remote.getFirmware()).isSameAs(remote.getFirmware());
    }

    @Test
    @DisplayName("Combined motors stop their outputs in one command")
    void shouldStopCombinedMotorsInOneCommand() {
        // Given
        RcxMotor sides = remote.getMotorA().and(remote.getMotorC());
        Infrared.replies.addAll(RcxFrames.replyTo(0x21));

        // When
        sides.stop();

        // Then
        assertThat(Infrared.written).as("off mode with outputs A and C").containsSubsequence(0x45, 0xBA);
    }

    @Test
    @DisplayName("Each motor drives its own output")
    void shouldDriveEachMotorOnItsOwnOutput() {
        // When
        remote.getMotorB().flt();

        // Then
        assertThat(Infrared.written).as("float mode with output B").containsSubsequence(0x02, 0xFD);
    }

    @Test
    @DisplayName("A light sensor on port 3 polls the brick's third input")
    void shouldPollTheThirdInputForALightSensorOnPort3() {
        // Given
        RcxLightSensor light = new RcxLightSensor(remote.getSensor3());
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 42, 0));

        // When
        int value = light.lightPercent();

        // Then
        assertThat(value).isEqualTo(42);
        assertThat(Infrared.written).as("sensor value source, input index 2").containsSubsequence(0x09, 0xF6, 0x02, 0xFD);
    }

    @Test
    @DisplayName("The battery reads the brick's millivolts")
    void shouldReadTheBatteryMillivolts() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x30, 0x28, 0x23));

        // When
        int millivolts = remote.getBattery().millivolts();

        // Then
        assertThat(millivolts).isEqualTo(0x2328);
    }

    @Test
    @DisplayName("A touch sensor configures its port for touch")
    void shouldConfigureItsPortForTouch() {
        // Given
        RcxTouchSensor touch = new RcxTouchSensor(remote.getSensor2());
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));

        // When
        boolean configured = touch.begin();

        // Then
        assertThat(configured).isTrue();
        assertThat(Infrared.written).as("touch type on input index 1").containsSubsequence(0x01, 0xFE, 0x01, 0xFE);
    }

    @Test
    @DisplayName("A rotation sensor reports signed steps as degrees")
    void shouldReportSignedRotationStepsAsDegrees() {
        // Given
        RcxRotationSensor rotation = new RcxRotationSensor(remote.getSensor1());
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0xF8, 0xFF));

        // When
        int degrees = rotation.degrees();

        // Then
        assertThat(degrees).as("-8 steps").isEqualTo(-180);
    }

    @Test
    @DisplayName("A temperature sensor reports tenths of a degree")
    void shouldReportTenthsOfADegree() {
        // Given
        RcxTemperatureSensor temperature = new RcxTemperatureSensor(remote.getSensor1());
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 235, 0));

        // When
        int tenths = temperature.temperatureTenths();

        // Then
        assertThat(tenths).isEqualTo(235);
    }

    @Test
    @DisplayName("Forward sets the forward direction, then switches the output on")
    void shouldSetForwardThenSwitchOnWhenGoingForward() {
        // When
        remote.getMotorC().forward();

        // Then
        assertThat(Infrared.written).as("forward C, then on C").containsSubsequence(0x84, 0x7B, 0x84, 0x7B);
    }

    @Test
    @DisplayName("Backward sets the backward direction, then switches the output on")
    void shouldSetBackwardThenSwitchOnWhenGoingBackward() {
        // When
        remote.getMotorA().backward();

        // Then
        assertThat(Infrared.written).as("backward A, then on A").containsSubsequence(0x01, 0xFE, 0x81, 0x7E);
    }

    @Test
    @DisplayName("Set power clamps the level on the motor's outputs")
    void shouldClampThePowerOnTheMotorsOutputs() {
        // When
        remote.getMotorB().setPower(-2);

        // Then
        assertThat(Infrared.written).as("output B, constant source, power 0").containsSubsequence(0x02, 0xFD, 0x02, 0xFD, 0x00, 0xFF);
    }

    @Test
    @DisplayName("Drive at zero brakes the motor")
    void shouldBrakeTheMotorWhenDrivingAtZero() {
        // When
        remote.getMotorA().drive(0);

        // Then
        assertThat(Infrared.written).as("off mode with output A").containsSubsequence(0x41, 0xBE);
    }

    @Test
    @DisplayName("The display shows a number and an icon")
    void shouldShowANumberAndAnIconOnTheDisplay() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x72));
        Infrared.replies.addAll(RcxFrames.replyTo(0xB2));
        Infrared.replies.addAll(RcxFrames.replyTo(0x82));

        // When
        boolean number = remote.getLcd().showNumber(7);
        boolean icon = remote.getLcd().showIcon(RcxLcd.ICON_DATALOG, true);
        boolean cleared = remote.getLcd().clear();

        // Then
        assertThat(new boolean[] {number, icon, cleared}).containsExactly(true, true, true);
    }

    @Test
    @DisplayName("The brick's buttons report the clicks, or -1 without a reply")
    void shouldReportTheBrickButtonsClicks() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, RcxButtons.VIEW | RcxButtons.RUN, 0x00));

        // When
        int clicked = remote.getButtons().clicked();
        int unanswered = remote.getButtons().clicked();

        // Then
        assertThat(clicked).isEqualTo(RcxButtons.VIEW | RcxButtons.RUN);
        assertThat(unanswered).isEqualTo(-1);
        assertThat(remote.getButtons()).isSameAs(remote.getButtons());
    }

    @Test
    @DisplayName("The speaker plays a sound and a tone")
    void shouldPlayASoundAndAToneOnTheSpeaker() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x51));
        Infrared.replies.addAll(RcxFrames.replyTo(0x23));

        // When
        boolean sound = remote.getAudio().playSound(RcxAudio.SOUND_SWEEP_UP);
        boolean tone = remote.getAudio().playTone(440, 50);

        // Then
        assertThat(new boolean[] {sound, tone}).containsExactly(true, true);
    }

    @Test
    @DisplayName("A pressed touch sensor reads one")
    void shouldReportAPressedTouchSensor() {
        // Given
        RcxTouchSensor touch = new RcxTouchSensor(remote.getSensor1());
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 1, 0));

        // When
        boolean pressed = touch.isPressed();

        // Then
        assertThat(pressed).isTrue();
    }

    @Test
    @DisplayName("Configuring the light, temperature and rotation sensors reports a silent brick")
    void shouldReportASilentBrickWhenConfiguringSensors() {
        // When
        boolean light = new RcxLightSensor(remote.getSensor1()).begin();
        boolean temperature = new RcxTemperatureSensor(remote.getSensor2()).begin();
        boolean rotation = new RcxRotationSensor(remote.getSensor3()).begin();

        // Then
        assertThat(new boolean[] {light, temperature, rotation}).containsExactly(false, false, false);
    }

    @Test
    @DisplayName("Resetting a rotation sensor clears its input")
    void shouldClearTheInputWhenResettingARotationSensor() {
        // When
        new RcxRotationSensor(remote.getSensor3()).reset();

        // Then
        assertThat(Infrared.written).as("clear input index 2").containsSubsequence(0x02, 0xFD);
    }
}
