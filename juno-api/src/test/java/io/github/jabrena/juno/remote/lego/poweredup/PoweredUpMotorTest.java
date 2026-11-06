package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Powered Up motor on a hub port")
class PoweredUpMotorTest {
    private PoweredUpPort port;
    private RemotePoweredUpHub hub;

    @BeforeEach
    void connectAHubToMockedHardware() {
        port = mock(PoweredUpPort.class);
        hub = new RemotePoweredUpHub(port, mock(Clock.class));
    }

    @Test
    @DisplayName("The hub returns the same motor for a port on every call")
    void shouldReturnTheSameMotorForAPort() {
        assertThat(hub.getMotor(HubPort.B)).isSameAs(hub.getMotor(HubPort.B));
    }

    @Test
    @DisplayName("Only the external ports A to D have motors")
    void shouldRejectTheTechnicSensorPorts() {
        assertThatThrownBy(() -> hub.getMotor(HubPort.TECHNIC_GYRO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Power is clamped to 0..100 and kept for the next movement")
    void shouldClampThePowerPercentage() {
        PoweredUpMotor motor = hub.getMotor(HubPort.A);

        motor.setPower(250);
        assertThat(motor.getPower()).isEqualTo(100);

        motor.setPower(-5);
        assertThat(motor.getPower()).isZero();
    }

    @Test
    @DisplayName("Forward and backward send the signed power to the port")
    void shouldDriveForwardAndBackward() {
        PoweredUpMotor motor = hub.getMotor(HubPort.A);
        motor.setPower(40);

        motor.forward();
        assertThat(motor.isMoving()).isTrue();
        verify(port).setMotorPower(HubPort.A.value(), 40);

        motor.backward();
        verify(port).setMotorPower(HubPort.A.value(), -40);
    }

    @Test
    @DisplayName("A power change while moving is sent at once")
    void shouldApplyAPowerChangeWhileMoving() {
        PoweredUpMotor motor = hub.getMotor(HubPort.C);
        motor.forward();

        motor.setPower(60);

        verify(port).setMotorPower(HubPort.C.value(), 60);
    }

    @Test
    @DisplayName("A power change while stopped is not sent")
    void shouldNotSendAPowerChangeWhileStopped() {
        PoweredUpMotor motor = hub.getMotor(HubPort.C);

        motor.setPower(60);

        verify(port, never()).setMotorPower(HubPort.C.value(), 60);
    }

    @Test
    @DisplayName("Stop brakes the motor and flt lets it coast")
    void shouldBrakeOrCoast() {
        PoweredUpMotor motor = hub.getMotor(HubPort.D);
        motor.forward();

        motor.stop();
        assertThat(motor.isMoving()).isFalse();
        verify(port).brakeMotor(HubPort.D.value());

        motor.forward();
        motor.flt();
        assertThat(motor.isMoving()).isFalse();
        verify(port).setMotorPower(HubPort.D.value(), 0);
    }

    @Test
    @DisplayName("Drive takes a signed percentage and coasts at zero")
    void shouldDriveWithASignedPercentage() {
        PoweredUpMotor motor = hub.getMotor(HubPort.A);

        motor.drive(-30);
        assertThat(motor.isMoving()).isTrue();
        verify(port).setMotorPower(HubPort.A.value(), -30);

        motor.drive(0);
        assertThat(motor.isMoving()).isFalse();
        verify(port).setMotorPower(HubPort.A.value(), 0);
    }

    @Test
    @DisplayName("Hold sends a zero speed that the hub holds")
    void shouldHoldTheMotor() {
        PoweredUpMotor motor = hub.getMotor(HubPort.B);
        motor.forward();

        motor.hold();

        assertThat(motor.isMoving()).isFalse();
        verify(port).holdMotor(HubPort.B.value());
    }

    @Test
    @DisplayName("Tacho count reads the hub position and resets to zero")
    void shouldCountFromZeroAfterReset() {
        when(port.readSensor(HubPort.A.value())).thenReturn(90);
        PoweredUpMotor motor = hub.getMotor(HubPort.A);

        assertThat(motor.getTachoCount()).isEqualTo(90);
        verify(port).enableSensor(HubPort.A.value(), RemotePoweredUpHub.MODE_MOTOR_POSITION);

        motor.resetTachoCount();
        when(port.readSensor(HubPort.A.value())).thenReturn(150);
        assertThat(motor.getTachoCount()).isEqualTo(60);
    }
}
