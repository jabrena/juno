package io.github.jabrena.juno.thirdparty.lego.rcx;

import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import io.github.jabrena.juno.thirdparty.ir.Infrared;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("RCX brick commands")
class RcxBrickTest {
    private RcxBrick brick;

    @BeforeEach
    void startWithAnEmptyLink() {
        Wire.resetBoard();
        Infrared.reset();
        brick = new RcxBrick(new RcxLink(SimulatedTime.clock()));
    }

    static Stream<Arguments> motorDirections() {
        return Stream.of(
                arguments(false, 0x01),
                arguments(true, 0x81));
    }

    @Test
    @DisplayName("Motor on sends its outputs in the on-off opcode")
    void shouldSendMotorOnOutputsInTheOnOffOpcode() {
        // When
        brick.motorOn(RcxBrick.OUTPUT_A | RcxBrick.OUTPUT_C);

        // Then
        assertThat(argumentPair(0)).isEqualTo(0x85);
    }

    @Test
    @DisplayName("Motor off uses the off mode")
    void shouldUseTheOffModeWhenMotorIsOff() {
        // When
        brick.motorOff(RcxBrick.OUTPUT_B);

        // Then
        assertThat(argumentPair(0)).isEqualTo(0x42);
    }

    @Test
    @DisplayName("Motor float uses the float mode")
    void shouldUseTheFloatModeWhenMotorFloats() {
        // When
        brick.motorFloat(RcxBrick.OUTPUT_B);

        // Then
        assertThat(argumentPair(0)).isEqualTo(0x02);
    }

    @ParameterizedTest(name = "direction reversed {0} encodes as {1}")
    @MethodSource("motorDirections")
    @DisplayName("Set motor direction encodes forward and backward")
    void shouldEncodeForwardAndBackwardMotorDirections(boolean reversed, int expected) {
        // When
        brick.setMotorDirection(RcxBrick.OUTPUT_A, reversed);

        // Then
        assertThat(argumentPair(0)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Set motor power clamps the level to the brick range")
    void shouldClampTheMotorPowerLevelToTheBrickRange() {
        // When
        brick.setMotorPower(RcxBrick.OUTPUT_B, 9);

        // Then
        assertThat(new int[] {argumentPair(0), argumentPair(1), argumentPair(2)})
                .containsExactly(RcxBrick.OUTPUT_B, 2, RcxBrick.MAX_POWER);
    }

    @Test
    @DisplayName("Drive stops a motor at zero speed")
    void shouldStopAMotorAtZeroSpeed() {
        // When
        brick.drive(RcxBrick.OUTPUT_A, 0);

        // Then
        assertThat(argumentPair(0)).isEqualTo(0x41);
    }

    @Test
    @DisplayName("Drive reverses a motor at negative speed with the direction first")
    void shouldReverseAMotorAtNegativeSpeedWithTheDirectionFirst() {
        // When
        brick.drive(RcxBrick.OUTPUT_A, -3);

        // Then
        assertThat(argumentPair(0)).as("direction first").isEqualTo(0x01);
        assertThat(Infrared.written).as("power and on follow").hasSizeGreaterThan(20);
    }

    @Test
    @DisplayName("Drive forward sets the forward direction first")
    void shouldSetTheForwardDirectionFirstWhenDrivingForward() {
        // When
        brick.drive(RcxBrick.OUTPUT_A, 3);

        // Then
        assertThat(Infrared.written).containsSubsequence(0x81, 0x7E);
    }

    @Test
    @DisplayName("Touch sensor is configured with its type and mode")
    void shouldConfigureTheTouchSensorWithItsTypeAndMode() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));

        // When
        boolean configured = brick.setTouchSensor(RcxBrick.INPUT_1);

        // Then
        assertThat(configured).isTrue();
    }

    @Test
    @DisplayName("Touch sensor configuration reports failure when the brick is silent")
    void shouldReportFailureWhenTheBrickIsSilentForTheTouchSensor() {
        // When
        boolean configured = brick.setTouchSensor(RcxBrick.INPUT_1);

        // Then
        assertThat(configured).isFalse();
    }

    @Test
    @DisplayName("Light sensor configuration reports failure when the brick does not answer")
    void shouldReportFailureWhenTheBrickDoesNotAnswerForTheLightSensor() {
        // When
        boolean configured = brick.setLightSensor(RcxBrick.INPUT_2);

        // Then
        assertThat(configured).isFalse();
    }

    @Test
    @DisplayName("Rotation sensor is configured")
    void shouldConfigureTheRotationSensor() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));

        // When
        boolean configured = brick.setRotationSensor(RcxBrick.INPUT_3);

        // Then
        assertThat(configured).isTrue();
    }

    @Test
    @DisplayName("Temperature sensor is configured")
    void shouldConfigureTheTemperatureSensor() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x32));
        Infrared.replies.addAll(RcxFrames.replyTo(0x42));

        // When
        boolean configured = brick.setTemperatureSensor(RcxBrick.INPUT_1);

        // Then
        assertThat(configured).isTrue();
    }

    @Test
    @DisplayName("Rotation steps are sign-extended")
    void shouldSignExtendRotationSteps() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0xFF, 0xFF));

        // When
        int steps = brick.rotationSteps(RcxBrick.INPUT_3);

        // Then
        assertThat(steps).isEqualTo(-1);
    }

    @Test
    @DisplayName("Rotation degrees are read from the two reply bytes")
    void shouldReadRotationDegreesFromTheTwoReplyBytes() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x08, 0x00));

        // When
        int degrees = brick.rotationDegrees(RcxBrick.INPUT_3);

        // Then
        assertThat(degrees).isEqualTo(180);
    }

    @Test
    @DisplayName("Temperature is reported in tenths of a degree")
    void shouldReportTemperatureInTenthsOfADegree() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x2B, 0x01));

        // When
        int tenths = brick.temperatureTenths(RcxBrick.INPUT_1);

        // Then
        assertThat(tenths).isEqualTo(299);
    }

    @Test
    @DisplayName("Sensor reading assembles its two bytes")
    void shouldAssembleASensorReadingFromItsTwoBytes() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x34, 0x12));

        // When
        int reading = brick.readSensor(RcxBrick.INPUT_2);

        // Then
        assertThat(reading).isEqualTo(0x1234);
    }

    @Test
    @DisplayName("Battery voltage is read in millivolts")
    void shouldReadTheBatteryVoltageInMillivolts() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x30, 0x20, 0x03));

        // When
        int millivolts = brick.batteryMillivolts();

        // Then
        assertThat(millivolts).isEqualTo(0x0320);
    }

    @Test
    @DisplayName("Battery voltage reports no reading without an answer")
    void shouldReportNoBatteryReadingWithoutAnAnswer() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x30));

        // When
        int millivolts = brick.batteryMillivolts();

        // Then
        assertThat(millivolts).as("no reading").isEqualTo(-1);
    }

    @Test
    @DisplayName("Touch sensor reports pressed when it reads one")
    void shouldReportPressedWhenTheTouchSensorReadsOne() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x01, 0x00));

        // When
        boolean pressed = brick.isPressed(RcxBrick.INPUT_1);

        // Then
        assertThat(pressed).isTrue();
    }

    @Test
    @DisplayName("Touch sensor reports released when it reads zero")
    void shouldReportReleasedWhenTheTouchSensorReadsZero() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x12, 0x00, 0x00));

        // When
        boolean pressed = brick.isPressed(RcxBrick.INPUT_1);

        // Then
        assertThat(pressed).isFalse();
    }

    @Test
    @DisplayName("Playing a sound is acknowledged by the brick")
    void shouldAcknowledgeAPlayedSound() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x51));

        // When
        boolean played = brick.playSound(RcxBrick.SOUND_BEEP);

        // Then
        assertThat(played).isTrue();
    }

    @Test
    @DisplayName("Showing a number is acknowledged and clamped to minus 9999")
    void shouldAcknowledgeAShownNumberClampedToMinus9999() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x72));

        // When
        boolean shown = brick.showNumber(-12_345);

        // Then
        assertThat(shown).isTrue();
        assertThat(Infrared.written).as("clamped to -9999: 0xD8F1 sent low byte first")
                .containsSubsequence(0xF1, 0x0E, 0xD8, 0x27);
    }

    @Test
    @DisplayName("Showing an icon is acknowledged by the brick")
    void shouldAcknowledgeAShownIcon() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0xB2));

        // When
        boolean shown = brick.showIcon(RcxBrick.ICON_WALKING, true);

        // Then
        assertThat(shown).isTrue();
    }

    @Test
    @DisplayName("Playing a tone is acknowledged by the brick")
    void shouldAcknowledgeAPlayedTone() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x23));

        // When
        boolean played = brick.playTone(440, 10);

        // Then
        assertThat(played).isTrue();
    }

    @Test
    @DisplayName("Playing a sound reports no acknowledgement when the brick is silent")
    void shouldReportNoAcknowledgementWhenTheBrickIsSilentForASound() {
        // When
        boolean played = brick.playSound(RcxBrick.SOUND_ERROR);

        // Then
        assertThat(played).as("no acknowledgement").isFalse();
    }

    @Test
    @DisplayName("Clear sensor sends its input without waiting for a reply")
    void shouldSendTheClearSensorInputWithoutWaitingForAReply() {
        // When
        brick.clearSensor(RcxBrick.INPUT_1);

        // Then
        assertThat(argumentPair(0)).isEqualTo(RcxBrick.INPUT_1);
    }

    @Test
    @DisplayName("Show number clamps to the display range")
    void shouldClampShowNumberToTheDisplayRange() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0x72));

        // When
        boolean shown = brick.showNumber(99_999);

        // Then
        assertThat(shown).isTrue();
        assertThat(Infrared.written).containsSubsequence(0x0F, 0xF0, 0x27, 0xD8);
    }

    @Test
    @DisplayName("Show icon can turn an icon off")
    void shouldTurnAnIconOffWhenShowingItWithFalse() {
        // Given
        Infrared.replies.addAll(RcxFrames.replyTo(0xB2));

        // When
        boolean shown = brick.showIcon(RcxBrick.ICON_WALKING, false);

        // Then
        assertThat(shown).isTrue();
        assertThat(Infrared.written).containsSubsequence(0x07, 0xF8, 0x00, 0xFF);
    }

    /** The value of argument {@code index}: the byte after the code in the packet written last. */
    private static int argumentPair(int index) {
        return Infrared.written.get(5 + index * 2);
    }
}
