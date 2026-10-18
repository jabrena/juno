package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.api.testing.Wire;
import io.github.jabrena.juno.api.thirdparty.ir.Infrared;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RcxBrickTest {

    @BeforeEach
    void startWithAnEmptyLink() {
        Wire.resetBoard();
        Infrared.reset();
    }

    @Test
    void motorOnSendsItsOutputsInTheOnOffOpcode() {
        RcxBrick.motorOn(RcxBrick.OUTPUT_A | RcxBrick.OUTPUT_C);

        assertThat(argumentPair(0)).isEqualTo(0x85);
    }

    @Test
    void motorOffAndFloatUseTheirOwnModes() {
        RcxBrick.motorOff(RcxBrick.OUTPUT_B);
        assertThat(argumentPair(0)).isEqualTo(0x42);

        Infrared.written.clear();
        RcxBrick.motorFloat(RcxBrick.OUTPUT_B);
        assertThat(argumentPair(0)).isEqualTo(0x02);
    }

    @Test
    void setMotorDirectionEncodesForwardAndBackward() {
        RcxBrick.setMotorDirection(RcxBrick.OUTPUT_A, false);
        assertThat(argumentPair(0)).isEqualTo(0x01);

        Infrared.written.clear();
        RcxBrick.setMotorDirection(RcxBrick.OUTPUT_A, true);
        assertThat(argumentPair(0)).isEqualTo(0x81);
    }

    @Test
    void setMotorPowerClampsTheLevelToTheBrickRange() {
        RcxBrick.setMotorPower(RcxBrick.OUTPUT_B, 9);

        assertThat(argumentPair(0)).isEqualTo(RcxBrick.OUTPUT_B);
        assertThat(argumentPair(1)).isEqualTo(2);
        assertThat(argumentPair(2)).isEqualTo(RcxBrick.MAX_POWER);
    }

    @Test
    void driveStopsForZeroAndReversesForNegativeSpeeds() {
        RcxBrick.drive(RcxBrick.OUTPUT_A, 0);
        assertThat(argumentPair(0)).isEqualTo(0x41);

        Infrared.written.clear();
        RcxBrick.drive(RcxBrick.OUTPUT_A, -3);
        assertThat(argumentPair(0)).as("direction first").isEqualTo(0x01);
        assertThat(Infrared.written).as("power and on follow").hasSizeGreaterThan(20);
    }

    @Test
    void touchSensorIsConfiguredWithItsTypeAndMode() {
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));

        assertThat(RcxBrick.setTouchSensor(RcxBrick.INPUT_1)).isTrue();
    }

    @Test
    void configuringASensorReportsAFailureWhenTheBrickDoesNotAnswer() {
        assertThat(RcxBrick.setLightSensor(RcxBrick.INPUT_2)).isFalse();
    }

    @Test
    void rotationAndTemperatureSensorsConfigureAndSignExtend() {
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));
        assertThat(RcxBrick.setRotationSensor(RcxBrick.INPUT_3)).isTrue();

        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));
        assertThat(RcxBrick.setTemperatureSensor(RcxBrick.INPUT_1)).isTrue();

        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0xFF, 0xFF));
        assertThat(RcxBrick.rotationSteps(RcxBrick.INPUT_3)).isEqualTo(-1);

        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x08, 0x00));
        assertThat(RcxBrick.rotationDegrees(RcxBrick.INPUT_3)).isEqualTo(180);

        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x2B, 0x01));
        assertThat(RcxBrick.temperatureTenths(RcxBrick.INPUT_1)).isEqualTo(299);
    }

    @Test
    void sensorReadingsAndBatteryAssembleTheirTwoBytes() {
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x34, 0x12));
        assertThat(RcxBrick.readSensor(RcxBrick.INPUT_2)).isEqualTo(0x1234);

        Infrared.replies.addAll(RcxFrames.replyTo(0x30, 0x20, 0x03));
        assertThat(RcxBrick.batteryMillivolts()).isEqualTo(0x0320);

        Infrared.replies.addAll(RcxFrames.replyTo(0x30));
        assertThat(RcxBrick.batteryMillivolts()).as("no reading").isEqualTo(-1);
    }

    @Test
    void isPressedReadsAOneFromTheTouchSensor() {
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x01, 0x00));
        assertThat(RcxBrick.isPressed(RcxBrick.INPUT_1)).isTrue();

        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x00, 0x00));
        assertThat(RcxBrick.isPressed(RcxBrick.INPUT_1)).isFalse();
    }

    @Test
    void soundNumberIconAndToneCommandsReportAcknowledgement() {
        Infrared.replies.addAll(RcxFrames.replyTo(0x51));
        assertThat(RcxBrick.playSound(RcxBrick.SOUND_BEEP)).isTrue();

        Infrared.replies.addAll(RcxFrames.replyTo(0x72));
        assertThat(RcxBrick.showNumber(-12_345)).isTrue();
        assertThat(Infrared.written).as("clamped to -9999: 0xD8F1 sent low byte first")
                .containsSubsequence(0xF1, 0x0E, 0xD8, 0x27);

        Infrared.replies.addAll(RcxFrames.replyTo(0xB2));
        assertThat(RcxBrick.showIcon(RcxBrick.ICON_WALKING, true)).isTrue();

        Infrared.replies.addAll(RcxFrames.replyTo(0x23));
        assertThat(RcxBrick.playTone(440, 10)).isTrue();

        assertThat(RcxBrick.playSound(RcxBrick.SOUND_ERROR)).as("no acknowledgement").isFalse();
    }

    @Test
    void clearSensorSendsItsInputWithoutWaitingForAReply() {
        RcxBrick.clearSensor(RcxBrick.INPUT_1);

        assertThat(argumentPair(0)).isEqualTo(RcxBrick.INPUT_1);
    }

    /** The value of argument {@code index}: the byte after the code in the packet written last. */
    private static int argumentPair(int index) {
        return Infrared.written.get(5 + index * 2);
    }

    @Test
    void driveForwardSetsTheForwardDirectionFirst() {
        RcxBrick.drive(RcxBrick.OUTPUT_A, 3);

        assertThat(Infrared.written).containsSubsequence(0x81, 0x7E);
    }

    @Test
    void showNumberClampsToTheDisplayRange() {
        Infrared.replies.addAll(RcxFrames.replyTo(0x72));

        assertThat(RcxBrick.showNumber(99_999)).isTrue();
        assertThat(Infrared.written).containsSubsequence(0x0F, 0xF0, 0x27, 0xD8);
    }

    @Test
    void showIconCanTurnAnIconOff() {
        Infrared.replies.addAll(RcxFrames.replyTo(0xB2));

        assertThat(RcxBrick.showIcon(RcxBrick.ICON_WALKING, false)).isTrue();
        assertThat(Infrared.written).containsSubsequence(0x07, 0xF8, 0x00, 0xFF);
    }

    @Test
    void setTouchSensorReportsFailureWhenTheBrickIsSilent() {
        assertThat(RcxBrick.setTouchSensor(RcxBrick.INPUT_1)).isFalse();
    }
}
