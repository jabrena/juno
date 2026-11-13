package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RemotePoweredUpHub with PoweredUpPort, BuiltInSensor and LinkedMotors")
class RemotePoweredUpHubPortsTest {
    private PoweredUpTransport port;
    private RemotePoweredUpHub hub;

    @BeforeEach
    void connectAHubToMockedHardware() {
        port = mock(PoweredUpTransport.class);
        hub = new RemotePoweredUpHub(port, mock(Clock.class));
    }

    @Test
    @DisplayName("External ports A to D reach the port with their protocol numbers")
    void shouldDelegateExternalPorts() {
        hub.getMotor(PoweredUpPort.C).drive(20);
        hub.enableSensor(PoweredUpPort.D, RemotePoweredUpHub.MODE_COLOR);
        when(port.readSensor(PoweredUpPort.B.value())).thenReturn(4);
        when(port.portDevice(PoweredUpPort.A.value())).thenReturn(RemotePoweredUpHub.DEVICE_TECHNIC_LARGE_MOTOR);

        verify(port).setMotorPower(PoweredUpPort.C.value(), 20);
        verify(port).enableSensor(3, RemotePoweredUpHub.MODE_COLOR);
        assertThat(hub.readSensor(PoweredUpPort.B)).isEqualTo(4);
        assertThat(hub.portDevice(PoweredUpPort.A)).isEqualTo(RemotePoweredUpHub.DEVICE_TECHNIC_LARGE_MOTOR);
    }

    @Test
    @DisplayName("Built-in sensors use their protocol numbers and never a motor")
    void shouldDelegateBuiltInSensors() {
        when(port.readSensorValue(BuiltInSensor.TECHNIC_GYRO.value(), 2, 2)).thenReturn(-8);
        when(port.sensorReportCount(BuiltInSensor.TECHNIC_TILT.value())).thenReturn(5);
        when(port.sensorReportSize(BuiltInSensor.TECHNIC_VOLTAGE.value())).thenReturn(2);

        hub.enableSensor(BuiltInSensor.TECHNIC_GYRO, RemotePoweredUpHub.MODE_IMU_VALUES);
        hub.readSensor(BuiltInSensor.TECHNIC_TILT);

        verify(port).enableSensor(0x62, RemotePoweredUpHub.MODE_IMU_VALUES);
        verify(port).readSensor(0x63);
        assertThat(hub.readSensorValue(BuiltInSensor.TECHNIC_GYRO, 2, 2)).isEqualTo(-8);
        assertThat(hub.sensorReportCount(BuiltInSensor.TECHNIC_TILT)).isEqualTo(5);
        assertThat(hub.sensorReportSize(BuiltInSensor.TECHNIC_VOLTAGE)).isEqualTo(2);
    }

    @Test
    @DisplayName("Deprecated HubPort overloads reach the same protocol numbers")
    void shouldKeepHubPortOverloadsWorking() {
        hub.getMotor(HubPort.B).drive(10);
        when(port.linkMotors(0, 1)).thenReturn(9);
        int pair = hub.linkMotors(HubPort.A, HubPort.B);

        verify(port).setMotorPower(1, 10);
        assertThat(pair).isEqualTo(9);
    }

    @Test
    @DisplayName("A linked pair drives both motors while the connection lasts")
    void shouldDriveLinkedPairWhileConnected() {
        when(port.connect(500)).thenReturn(true);
        when(port.isConnected()).thenReturn(true);
        when(port.linkMotors(0, 1)).thenReturn(7);
        hub.connect(500);

        LinkedMotors pair = hub.linkMotors(PoweredUpPort.A, PoweredUpPort.B);
        pair.setPower(30, -30);
        pair.brake();

        verify(port).setLinkedMotorPower(7, 30, -30);
        verify(port).brakeLinkedMotors(7);
    }

    @Test
    @DisplayName("A linked pair is invalid after disconnect and after a reconnect")
    void shouldInvalidateLinkedPairAfterDisconnect() {
        when(port.connect(500)).thenReturn(true);
        when(port.isConnected()).thenReturn(true);
        when(port.linkMotors(0, 1)).thenReturn(7);
        hub.connect(500);
        LinkedMotors pair = hub.linkMotors(PoweredUpPort.A, PoweredUpPort.B);

        hub.disconnect();

        assertThatThrownBy(() -> pair.setPower(10, 10)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(pair::brake).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(pair::unlink).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A linked pair is invalid once the hub is out of range")
    void shouldInvalidateLinkedPairWhenHubDrops() {
        when(port.connect(500)).thenReturn(true);
        when(port.isConnected()).thenReturn(true);
        when(port.linkMotors(0, 1)).thenReturn(7);
        hub.connect(500);
        LinkedMotors pair = hub.linkMotors(PoweredUpPort.A, PoweredUpPort.B);

        when(port.isConnected()).thenReturn(false);

        assertThatThrownBy(() -> pair.setPower(10, 10)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A linked pair cannot be used after unlink")
    void shouldRejectUseAfterUnlink() {
        when(port.connect(500)).thenReturn(true);
        when(port.isConnected()).thenReturn(true);
        when(port.linkMotors(0, 1)).thenReturn(7);
        hub.connect(500);
        LinkedMotors pair = hub.linkMotors(PoweredUpPort.A, PoweredUpPort.B);

        pair.unlink();

        verify(port).unlinkMotors(7);
        assertThatThrownBy(() -> pair.setPower(10, 10)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Linking fails loudly when the hub does not create the pair")
    void shouldFailWhenHubDoesNotLink() {
        when(port.linkMotors(0, 1)).thenReturn(-1);

        assertThatThrownBy(() -> hub.linkMotors(PoweredUpPort.A, PoweredUpPort.B))
                .isInstanceOf(IllegalStateException.class);
    }
}
