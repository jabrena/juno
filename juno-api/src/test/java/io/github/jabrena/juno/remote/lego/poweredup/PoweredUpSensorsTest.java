package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.robotics.SampleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Technic Hub motion sensors: accelerometer, gyroscope and tilt")
class PoweredUpSensorsTest {
    private static final int ACCELEROMETER = HubPort.TECHNIC_ACCELEROMETER.value();
    private static final int GYRO = HubPort.TECHNIC_GYRO.value();
    private static final int TILT = HubPort.TECHNIC_TILT.value();
    private PoweredUpTransport port;
    private Clock time;
    private RemotePoweredUpHub hub;

    @BeforeEach
    void connectAHubToMockedHardware() {
        port = mock(PoweredUpTransport.class);
        time = mock(Clock.class);
        hub = new RemotePoweredUpHub(port, time);
    }

    private void connectTechnicHub() {
        when(port.connect(anyInt())).thenReturn(true);
        when(port.hubType()).thenReturn(RemotePoweredUpHub.TYPE_TECHNIC_HUB);
        hub.connect(1000);
    }

    @Test
    @DisplayName("The hub returns the same accelerometer, gyroscope and tilt sensor on every call")
    void shouldReturnTheSameSensorsOnEveryCall() {
        assertThat(hub.getAccelerometer()).isSameAs(hub.getAccelerometer());
        assertThat(hub.getGyroscope()).isSameAs(hub.getGyroscope());
        assertThat(hub.getTilt()).isSameAs(hub.getTilt());
    }

    @Test
    @DisplayName("Connecting to a Technic Hub subscribes all three motion sensors to their own ports")
    void shouldSubscribeTheMotionSensorsOnConnect() {
        connectTechnicHub();

        verify(port).enableSensor(ACCELEROMETER, RemotePoweredUpHub.MODE_IMU_VALUES);
        verify(port).enableSensor(GYRO, RemotePoweredUpHub.MODE_IMU_VALUES);
        verify(port).enableSensor(TILT, RemotePoweredUpHub.MODE_IMU_VALUES);
    }

    @Test
    @DisplayName("Connecting to a hub without these sensors subscribes nothing")
    void shouldNotSubscribeOnAnotherHubType() {
        when(port.connect(anyInt())).thenReturn(true);
        when(port.hubType()).thenReturn(RemotePoweredUpHub.TYPE_CITY_HUB);

        hub.connect(1000);

        verify(port, never()).enableSensor(anyInt(), anyInt());
    }

    @Test
    @DisplayName("A read before the first report returns zero and does not wait for the hub")
    void shouldReadZeroWithoutWaiting() {
        Accelerometer accelerometer = hub.getAccelerometer();

        assertThat(accelerometer.getXAccel()).isZero();
        verify(time, never()).millis();
    }

    @Test
    @DisplayName("A read processes the hub's pending reports before taking the value")
    void shouldProcessPendingReportsOnRead() {
        ((SampleProvider) hub.getGyroscope()).sampleSize();
        float[] sample = new float[3];

        ((SampleProvider) hub.getGyroscope()).fetchSample(sample, 0);

        verify(port).readSensor(GYRO);
    }

    @Test
    @DisplayName("An acceleration axis is reported in whole metres per second squared")
    void shouldReportAccelerationInMetresPerSecondSquared() {
        // Given: 1000 milli-g is one g, about 9.8 m/s^2
        when(port.readSensorValue(ACCELEROMETER, 0, 2)).thenReturn(1000);
        when(port.readSensorValue(ACCELEROMETER, 2, 2)).thenReturn(-2000);
        Accelerometer accelerometer = hub.getAccelerometer();

        // When & Then
        assertThat(accelerometer.getXAccel()).isEqualTo(9);
        assertThat(accelerometer.getZAccel()).isEqualTo(-19);
    }

    @Test
    @DisplayName("The accelerometer samples its raw-scale values in metres per second squared")
    void shouldSampleAccelerationValues() {
        when(port.readSensorValue(ACCELEROMETER, 0, 2)).thenReturn(1000);
        when(port.readSensorValue(ACCELEROMETER, 1, 2)).thenReturn(0);
        when(port.readSensorValue(ACCELEROMETER, 2, 2)).thenReturn(-500);
        SampleProvider accelerometer = (SampleProvider) hub.getAccelerometer();
        float[] sample = new float[3];

        accelerometer.fetchSample(sample, 0);

        assertThat(accelerometer.sampleSize()).isEqualTo(3);
        assertThat(sample[0]).isCloseTo(9.80665f, within(0.001f));
        assertThat(sample[2]).isCloseTo(-4.903f, within(0.001f));
    }

    @Test
    @DisplayName("The tilt sensor samples yaw, pitch and roll in degrees")
    void shouldSampleTheTiltAngles() {
        when(port.readSensorValue(TILT, 0, 2)).thenReturn(30);
        when(port.readSensorValue(TILT, 1, 2)).thenReturn(-4);
        when(port.readSensorValue(TILT, 2, 2)).thenReturn(7);
        float[] sample = new float[3];

        hub.getTilt().fetchSample(sample, 0);

        assertThat(sample).containsExactly(30f, -4f, 7f);
    }

    @Test
    @DisplayName("The gyroscope's heading is zero at the tilt yaw where reset ran, and grows clockwise")
    void shouldMeasureTheHeadingFromTheTiltYawAtReset() {
        // Given
        when(port.readSensorValue(TILT, 0, 2)).thenReturn(30);
        Gyroscope gyroscope = hub.getGyroscope();
        gyroscope.reset();

        // When
        when(port.readSensorValue(TILT, 0, 2)).thenReturn(75);

        // Then
        assertThat(gyroscope.getAngle()).isEqualTo(45);
    }

    @Test
    @DisplayName("The gyroscope's heading wraps from 359 to 0 across the yaw's -180 to 180 range")
    void shouldWrapTheHeadingAcrossTheYawRange() {
        // Given
        when(port.readSensorValue(TILT, 0, 2)).thenReturn(170);
        Gyroscope gyroscope = hub.getGyroscope();
        gyroscope.reset();

        // When
        when(port.readSensorValue(TILT, 0, 2)).thenReturn(-170);

        // Then
        assertThat(gyroscope.getAngle()).isEqualTo(20);
    }

    @Test
    @DisplayName("The gyroscope's angular velocity is the gyro's yaw rate in degrees per second")
    void shouldReportTheGyroYawRate() {
        when(port.readSensorValue(GYRO, 2, 2)).thenReturn(45);

        assertThat(hub.getGyroscope().getAngularVelocity()).isEqualTo(45f);
    }

    @Test
    @DisplayName("The gyroscope's sample is its rotation rate about X, Y and Z")
    void shouldSampleTheRotationRate() {
        when(port.readSensorValue(GYRO, 0, 2)).thenReturn(-12);
        SampleProvider gyroscope = (SampleProvider) hub.getGyroscope();
        float[] sample = new float[3];

        gyroscope.fetchSample(sample, 0);

        assertThat(gyroscope.sampleSize()).isEqualTo(3);
        assertThat(sample[0]).isEqualTo(-12f);
    }
}
