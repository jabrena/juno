package io.github.jabrena.juno.hardware.device.motor;

import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.robotics.DCMotor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Grove I2C Motor Driver TB6612FNG")
class GroveMotorDriverTb6612fngTest {
    private static final int ADDRESS = 0x14;

    private I2c i2c;
    private final List<List<Integer>> frames = new ArrayList<>();
    private GroveMotorDriverTb6612fng driver;
    private DCMotor motorA;
    private DCMotor motorB;

    @BeforeEach
    void connectTheDriverToAMockedI2cBus() {
        i2c = mock(I2c.class);
        when(i2c.write(anyInt(), any(byte[].class), anyInt())).thenAnswer(this::record);
        driver = new GroveMotorDriverTb6612fng(i2c);
        motorA = driver.getMotorA();
        motorB = driver.getMotorB();
    }

    /** Copies each frame as it is sent, because the driver reuses one buffer. */
    private Integer record(InvocationOnMock invocation) {
        byte[] data = invocation.getArgument(1);
        int length = invocation.getArgument(2);
        List<Integer> frame = new ArrayList<>();
        for (int index = 0; index < length; index++) {
            frame.add(data[index] & 0xFF);
        }
        frames.add(frame);
        return length;
    }

    @Test
    @DisplayName("Construction starts the bus, probes the driver and leaves standby")
    void shouldProbeAndLeaveStandbyWhenConstructed() {
        verify(i2c).begin();
        assertThat(frames).containsExactly(List.of(), List.of(0x05, 0x00));
    }

    @Test
    @DisplayName("A driver that does not acknowledge is rejected at startup")
    void shouldRejectAnAbsentDriver() {
        I2c silent = mock(I2c.class);
        when(silent.write(anyInt(), any(byte[].class), anyInt())).thenReturn(-1);

        assertThatThrownBy(() -> new GroveMotorDriverTb6612fng(silent))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Only addresses 0x01 to 0x7F are accepted")
    void shouldRejectAddressesOutsideTheRange() {
        assertThatThrownBy(() -> new GroveMotorDriverTb6612fng(i2c, 0x00))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GroveMotorDriverTb6612fng(i2c, 0x80))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Forward on channel A sends clockwise with the power scaled to PWM")
    void shouldDriveChannelAClockwise() {
        motorA.setPower(50);

        motorA.forward();

        assertThat(frames.getLast()).containsExactly(0x02, 0x00, 127);
        assertThat(motorA.isMoving()).isTrue();
    }

    @Test
    @DisplayName("Backward on channel B sends counter-clockwise at full power")
    void shouldDriveChannelBCounterClockwise() {
        motorB.setPower(100);

        motorB.backward();

        assertThat(frames.getLast()).containsExactly(0x03, 0x01, 0xFF);
        assertThat(motorA.isMoving()).isFalse();
    }

    @Test
    @DisplayName("Changing power while moving resends the current direction at the new speed")
    void shouldResendWhenPowerChangesWhileMoving() {
        motorB.setPower(20);
        motorB.forward();

        motorB.setPower(40);

        assertThat(frames.getLast()).containsExactly(0x02, 0x01, 102);
        assertThat(motorB.isMoving()).isTrue();
    }

    @Test
    @DisplayName("Stop brakes the channel and Flt lets it coast")
    void shouldBrakeOrCoast() {
        motorA.stop();
        assertThat(frames.getLast()).containsExactly(0x00, 0x00);
        assertThat(motorA.isMoving()).isFalse();

        motorB.flt();
        assertThat(frames.getLast()).containsExactly(0x01, 0x01);
        assertThat(motorB.isMoving()).isFalse();
    }

    @Test
    @DisplayName("Power is clamped to 0..100 and zero power is not movement")
    void shouldClampPower() {
        motorA.setPower(150);
        assertThat(motorA.getPower()).isEqualTo(100);

        motorA.setPower(-5);
        assertThat(motorA.getPower()).isZero();

        motorA.forward();
        assertThat(motorA.isMoving()).isFalse();
    }

    @Test
    @DisplayName("Construction with a custom address sends frames to that address")
    void shouldUseTheConfiguredAddress() {
        I2c bus = mock(I2c.class);
        new GroveMotorDriverTb6612fng(bus, 0x2A);

        verify(bus).write(eq(0x2A), any(byte[].class), eq(0));
        verify(bus).write(eq(0x2A), any(byte[].class), eq(2));
    }
}
