package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PoweredUpHubRemote over a port")
class PoweredUpHubRemoteDelegationTest {
    private PoweredUpPort port;
    private Clock time;
    private PoweredUpHubRemote hub;

    @BeforeEach
    void connectAHubToMockedHardware() {
        port = mock(PoweredUpPort.class);
        time = mock(Clock.class);
        hub = new PoweredUpHubRemote(port, time);
    }

    @Test
    @DisplayName("Connection and status calls reach the port")
    void shouldDelegateConnectionAndStatus() {
        when(port.connect(500)).thenReturn(true);
        when(port.isConnected()).thenReturn(true);
        when(port.hubType()).thenReturn(PoweredUpHubRemote.TYPE_CITY_HUB);
        when(port.batteryPercent()).thenReturn(80);
        when(port.buttonPressed()).thenReturn(false);
        when(port.rssi()).thenReturn(-60);
        when(port.firmwareVersion()).thenReturn(0x12345678);
        when(port.hardwareVersion()).thenReturn(0x42);
        when(port.portDevice(PoweredUpHubRemote.PORT_B)).thenReturn(PoweredUpHubRemote.DEVICE_MEDIUM_LINEAR_MOTOR);
        byte[] name = new byte[8];
        when(port.hubName(name, 8)).thenReturn(5);

        assertThat(hub.connect(500)).isTrue();
        assertThat(hub.isConnected()).isTrue();
        assertThat(hub.hubType()).isEqualTo(PoweredUpHubRemote.TYPE_CITY_HUB);
        assertThat(hub.batteryPercent()).isEqualTo(80);
        assertThat(hub.buttonPressed()).isFalse();
        assertThat(hub.rssi()).isEqualTo(-60);
        assertThat(hub.firmwareVersion()).isEqualTo(0x12345678);
        assertThat(hub.hardwareVersion()).isEqualTo(0x42);
        assertThat(hub.portDevice(PoweredUpHubRemote.PORT_B)).isEqualTo(PoweredUpHubRemote.DEVICE_MEDIUM_LINEAR_MOTOR);
        assertThat(hub.hubName(name, 8)).isEqualTo(5);
    }

    @Test
    @DisplayName("Motor, LED and linked-motor commands reach the port")
    void shouldDelegateMotorLedAndLinkCommands() {
        when(port.linkMotors(0, 1)).thenReturn(7);
        when(port.readSensor(PoweredUpHubRemote.PORT_A)).thenReturn(90);

        hub.setMotorPower(PoweredUpHubRemote.PORT_A, 40);
        hub.brakeMotor(PoweredUpHubRemote.PORT_A);
        hub.holdMotor(PoweredUpHubRemote.PORT_A);
        hub.setLedColor(PoweredUpHubRemote.COLOR_BLUE);
        hub.setLedRgb(255, 128, 0);
        int pair = hub.linkMotors(0, 1);
        hub.setLinkedMotorPower(pair, 30, -30);
        hub.brakeLinkedMotors(pair);
        hub.unlinkMotors(pair);

        verify(port).setMotorPower(PoweredUpHubRemote.PORT_A, 40);
        verify(port).brakeMotor(PoweredUpHubRemote.PORT_A);
        verify(port).holdMotor(PoweredUpHubRemote.PORT_A);
        verify(port).setLedColor(PoweredUpHubRemote.COLOR_BLUE);
        verify(port).setLedRgb(255, 128, 0);
        verify(port).setLinkedMotorPower(7, 30, -30);
        verify(port).brakeLinkedMotors(7);
        verify(port).unlinkMotors(7);
        assertThat(pair).isEqualTo(7);
        assertThat(hub.readSensor(PoweredUpHubRemote.PORT_A)).isEqualTo(90);
    }

    @Test
    @DisplayName("Sensor, report and shutdown calls reach the port")
    void shouldDelegateSensorReportsAndShutdown() {
        when(port.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_GYRO, 1, 2)).thenReturn(-16);
        when(port.sensorReportCount(PoweredUpHubRemote.PORT_A)).thenReturn(3);
        when(port.sensorReportSize(PoweredUpHubRemote.PORT_A)).thenReturn(2);

        hub.enableSensor(PoweredUpHubRemote.PORT_A, PoweredUpHubRemote.MODE_MOTOR_POSITION);
        hub.disconnect();
        hub.switchOff();

        verify(port).enableSensor(PoweredUpHubRemote.PORT_A, PoweredUpHubRemote.MODE_MOTOR_POSITION);
        verify(port).disconnect();
        verify(port).switchOff();
        assertThat(hub.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_GYRO, 1, 2)).isEqualTo(-16);
        assertThat(hub.sensorReportCount(PoweredUpHubRemote.PORT_A)).isEqualTo(3);
        assertThat(hub.sensorReportSize(PoweredUpHubRemote.PORT_A)).isEqualTo(2);
    }

    @Test
    @DisplayName("Battery voltage scales the internal sensor reading to millivolts")
    void shouldScaleTheVoltageReadingToMillivolts() {
        when(port.sensorReportSize(PoweredUpHubRemote.PORT_TECHNIC_VOLTAGE)).thenReturn(2);
        when(port.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_VOLTAGE, 0, 2)).thenReturn(2048);

        assertThat(hub.batteryMillivolts()).isEqualTo(2048 * 9615 / 4095);
    }

    @Test
    @DisplayName("Current reads the internal sensor after subscribing to it, then gives up after the wait")
    void shouldSubscribeToTheCurrentSensorAndGiveUpWhenItStaysSilent() {
        when(port.sensorReportSize(PoweredUpHubRemote.PORT_TECHNIC_CURRENT)).thenReturn(0);
        when(time.millis()).thenReturn(0, 100, 400);

        assertThat(hub.currentMilliamps()).isEqualTo(-1);
        verify(port).enableSensor(PoweredUpHubRemote.PORT_TECHNIC_CURRENT, PoweredUpHubRemote.MODE_IMU_VALUES);
        verify(port, org.mockito.Mockito.atLeastOnce()).readSensor(PoweredUpHubRemote.PORT_TECHNIC_CURRENT);
    }

    @Test
    @DisplayName("Current scales a sensor reading that has arrived")
    void shouldScaleTheCurrentReadingWhenItHasArrived() {
        when(port.sensorReportSize(PoweredUpHubRemote.PORT_TECHNIC_CURRENT)).thenReturn(2);
        when(port.readSensorValue(anyInt(), anyInt(), anyInt())).thenReturn(4095);

        assertThat(hub.currentMilliamps()).isEqualTo(4175);
    }
}
