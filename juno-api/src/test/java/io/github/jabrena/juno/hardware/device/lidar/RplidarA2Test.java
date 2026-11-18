package io.github.jabrena.juno.hardware.device.lidar;

import io.github.jabrena.juno.hardware.device.usb.Cp210xSerial;
import io.github.jabrena.juno.hardware.device.usb.FakeUsbBus;
import io.github.jabrena.juno.hardware.device.usb.UsbHostShield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RPLIDAR A2 over a USB Host Shield")
class RplidarA2Test {
    private FakeUsbBus bus;
    private RplidarA2 lidar;

    @BeforeEach
    void connectTheLidar() {
        bus = new FakeUsbBus();
        UsbHostShield usb = new UsbHostShield(bus.gpio, bus);
        usb.begin();
        lidar = new RplidarA2(new Cp210xSerial(usb));
    }

    @Test
    @DisplayName("Opens the adapter and stops any running scan")
    void shouldBegin() {
        assertThat(lidar.begin()).isTrue();
        assertThat(bus.lidarCommands).containsExactly(0x25);
    }

    @Test
    @DisplayName("Reports the adapter lost once a transfer fails")
    void shouldNoticeALostAdapter() {
        int[] scan = new int[RplidarA2.DEGREES];
        lidar.begin();
        assertThat(lidar.isConnected()).isTrue();
        bus.deadController = true;

        assertThat(lidar.poll(scan)).isFalse();
        assertThat(lidar.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Does not begin without the adapter")
    void shouldNotBeginWithoutTheAdapter() {
        bus.connected = false;

        assertThat(lidar.begin()).isFalse();
    }

    @Test
    @DisplayName("Reads health and device info")
    void shouldReadHealthAndInfo() {
        int[] info = new int[RplidarA2.INFO_LENGTH];
        lidar.begin();

        assertThat(lidar.health()).isEqualTo(RplidarA2.HEALTH_WARNING);
        assertThat(lidar.info(info)).isTrue();
        assertThat(info).containsExactly(0x28, 1, 20, 2);
    }

    @Test
    @DisplayName("Reports a lidar that does not answer")
    void shouldReportSilence() {
        int[] info = new int[RplidarA2.INFO_LENGTH];
        lidar.begin();
        bus.lidarAnswers = false;

        assertThat(lidar.health()).isEqualTo(RplidarA2.HEALTH_NO_ANSWER);
        assertThat(lidar.info(info)).isFalse();
        assertThat(lidar.startScan()).isFalse();
    }

    @Test
    @DisplayName("Ignores a response of another type")
    void shouldRejectAnotherResponseType() {
        lidar.begin();
        bus.lidarAnswers = false;
        bus.receive(0xA5, 0x5A, 0x14, 0, 0, 0, 0x04);

        assertThat(lidar.health()).isEqualTo(RplidarA2.HEALTH_NO_ANSWER);
    }

    @Test
    @DisplayName("Starts the motor at the default PWM and stops it again")
    void shouldDriveTheMotor() {
        lidar.begin();

        assertThat(lidar.startMotor()).isTrue();
        assertThat(lidar.stopMotor()).isTrue();

        assertThat(bus.motorPwm).containsExactly(RplidarA2.DEFAULT_MOTOR_PWM, 0);
    }

    @Test
    @DisplayName("Clamps the motor PWM to 0..1023")
    void shouldClampThePwm() {
        lidar.begin();

        lidar.setMotorPwm(5000);
        lidar.setMotorPwm(-3);

        assertThat(bus.motorPwm).containsExactly(RplidarA2.MAX_MOTOR_PWM, 0);
    }

    @Test
    @DisplayName("Collects a revolution of nodes into a per-degree scan, resynchronising on noise")
    void shouldCollectARevolution() {
        int[] scan = new int[RplidarA2.DEGREES];
        lidar.begin();
        assertThat(lidar.startScan()).isTrue();

        assertThat(lidar.poll(scan)).isFalse();
        stream(0xFF);
        stream(FakeUsbBus.node(true, 0, 100));
        stream(FakeUsbBus.node(false, 90, 200));
        stream(FakeUsbBus.node(false, 180, 300));
        stream(FakeUsbBus.node(false, 270, 400));
        stream(FakeUsbBus.node(true, 1, 500));

        assertThat(lidar.poll(scan)).isTrue();
        assertThat(scan[0]).isEqualTo(100);
        assertThat(scan[90]).isEqualTo(200);
        assertThat(scan[180]).isEqualTo(300);
        assertThat(scan[270]).isEqualTo(400);
        assertThat(scan[1]).isZero();
        assertThat(lidar.revolutions()).isEqualTo(1);
        assertThat(lidar.nodes()).isEqualTo(5);
        assertThat(lidar.poll(scan)).isFalse();
    }

    @Test
    @DisplayName("Returns after its byte budget, even when the stream never stops")
    void shouldBoundOnePoll() {
        int[] scan = new int[RplidarA2.DEGREES];
        lidar.begin();
        lidar.startScan();
        for (int i = 0; i < 300; i++) {
            stream(FakeUsbBus.node(false, i % 360, 1000));
        }

        assertThat(lidar.poll(scan)).isFalse();
        assertThat(lidar.nodes()).isBetween(200, 299);
    }

    @Test
    @DisplayName("Sends STOP and RESET")
    void shouldStopAndReset() {
        lidar.begin();

        lidar.stop();
        lidar.reset();

        assertThat(bus.lidarCommands).containsExactly(0x25, 0x25, 0x40);
    }

    private void stream(int... bytes) {
        for (int value : bytes) {
            bus.scanStream.add(value);
        }
    }
}
