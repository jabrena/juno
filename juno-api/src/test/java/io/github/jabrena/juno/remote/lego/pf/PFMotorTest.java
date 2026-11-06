package io.github.jabrena.juno.remote.lego.pf;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Power Functions motor")
class PFMotorTest {
    private static final int PAUSE_ONE = 546;
    /** Each repeat is a start mark, sixteen data marks and a stop mark: 18 mark/space pairs. */
    private static final int PAIRS_PER_REPEAT = 18;

    private RemotePF remote;

    @BeforeEach
    void startWithAnEmptyTransmitter() {
        Wire.resetBoard();
        Infrared.reset();
        PFLink link = new PFLink(new HX53InfraredEmitter(3));
        remote = new RemotePF(link);
    }

    @Test
    @DisplayName("A blue motor sends a single-output message to the blue port of its channel only")
    void shouldSendASingleOutputMessageToItsPort() {
        // Given
        PFMotor motor = new PFMotor(remote, PFChannel.CHANNEL_2, PFPort.BLUE);

        // When
        motor.setSpeed(3);

        // Then
        int message = decode(0);
        assertThat(new int[] {(message >> 12) & 0x3, (message >> 8) & 0xF, (message >> 4) & 0xF})
                .as("channel 2 (index 1), single output with the blue output selected, speed 3")
                .containsExactly(0x1, 0x5, 0x3);
    }

    @Test
    @DisplayName("A red motor sends its speed to the red port")
    void shouldSendItsSpeedToTheRedPort() {
        // Given
        PFMotor motor = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.RED);

        // When
        motor.setSpeed(-7);

        // Then
        int message = decode(0);
        assertThat(new int[] {(message >> 8) & 0xF, (message >> 4) & 0xF})
                .as("single output, red output selected, speed -7 is PWM nibble 9")
                .containsExactly(0x4, 0x9);
    }

    @Test
    @DisplayName("Flt lets its own port coast, and stop brakes it")
    void shouldCoastOrBrakeOnlyItsOwnPort() {
        // Given
        PFMotor motor = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.RED);

        // When
        motor.flt();

        // Then
        assertThat((decode(0) >> 4) & 0xF).as("zero speed is PWM float").isEqualTo(0x0);

        // When
        Infrared.pulses.clear();
        motor.stop();

        // Then
        assertThat((decode(0) >> 4) & 0xF).as("brake is PWM nibble 8").isEqualTo(0x8);
    }

    @Test
    @DisplayName("Forward at full power sends speed 7 and backward sends speed -7")
    void shouldDriveForwardAndBackwardAtFullPower() {
        // Given
        PFMotor motor = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.RED);

        // When
        motor.forward();

        // Then
        assertThat((decode(0) >> 4) & 0xF).as("speed 7").isEqualTo(0x7);

        // When
        Infrared.pulses.clear();
        motor.backward();

        // Then
        assertThat((decode(0) >> 4) & 0xF).as("speed -7 is PWM nibble 9").isEqualTo(0x9);
    }

    @Test
    @DisplayName("Power is scaled onto the seven speed steps when the motor drives")
    void shouldScalePowerOntoTheSpeedSteps() {
        // Given
        PFMotor motor = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.RED);
        motor.setPower(50);

        // When
        motor.forward();

        // Then
        assertThat((decode(0) >> 4) & 0xF).as("50 percent is speed 4").isEqualTo(0x4);
    }

    @Test
    @DisplayName("Power is clamped to 0..100 and read back")
    void shouldClampPowerAndReadItBack() {
        // Given
        PFMotor motor = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.RED);

        // When
        motor.setPower(250);

        // Then
        assertThat(motor.getPower()).isEqualTo(100);

        // When
        motor.setPower(-5);

        // Then
        assertThat(motor.getPower()).isZero();
    }

    @Test
    @DisplayName("Is moving reports the last command, and flt clears it")
    void shouldReportWhetherTheLastCommandIsMoving() {
        // Given
        PFMotor motor = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.RED);

        // When
        motor.forward();

        // Then
        assertThat(motor.isMoving()).isTrue();

        // When
        motor.flt();

        // Then
        assertThat(motor.isMoving()).isFalse();
    }

    private static int decode(int repeat) {
        int base = repeat * PAIRS_PER_REPEAT * 2;
        int message = 0;
        for (int bit = 0; bit < 16; bit++) {
            int space = -Infrared.pulses.get(base + 3 + 2 * bit);
            message = (message << 1) | (space == PAUSE_ONE ? 1 : 0);
        }
        return message;
    }
}
