package io.github.jabrena.juno.remote.lego.poweredup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The LEGO Wireless Protocol 3.0 frames and decodings of a City Hub, checked against the fake radio. Each expected frame
 * is the one the hub expects on the air: a length byte, the hub id 0, the message type, then its payload.
 */
@DisplayName("LEGO Wireless Protocol over the BLE radio")
class LegoWirelessPortTest {
    private FakeBluetoothLe radio;
    private LegoWirelessPort port;

    @BeforeEach
    void setUp() {
        radio = new FakeBluetoothLe();
        port = new LegoWirelessPort(radio, new FakeClock());
    }

    @Test
    @DisplayName("Connects to the first advertising hub and reads its type from the manufacturer data")
    void shouldConnectAndReadTheHubTypeFromTheAdvertisement() {
        // When
        boolean connected = port.connect(0);

        // Then
        assertThat(connected).isTrue();
        assertThat(port.isConnected()).isTrue();
        assertThat(port.hubType()).isEqualTo(0x41);
    }

    @Test
    @DisplayName("Reports no hub when none advertises in time")
    void shouldReportNoHubWhenNoneAdvertises() {
        // Given
        radio.setAdvertising(false);

        // When & Then
        assertThat(port.connect(100)).isFalse();
        assertThat(port.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Drops commands sent before connecting instead of queuing them")
    void shouldDropCommandsBeforeConnecting() {
        // When
        port.setMotorPower(0, 50);

        // Then
        assertThat(radio.frames()).isEmpty();
    }

    @Test
    @DisplayName("Writes motor power frames, clamped to plus or minus 100, and brake and hold frames")
    void shouldWriteMotorFramesClampedToThePowerRange() {
        // Given
        port.connect(0);

        // When
        port.setMotorPower(0, 50);
        port.setMotorPower(1, -150);
        port.setMotorPower(3, 101);
        port.brakeMotor(0);
        port.holdMotor(0);

        // Then
        assertThat(radio.frames()).containsExactly(
                "08 00 81 00 11 51 00 32",
                "08 00 81 01 11 51 00 9C",
                "08 00 81 03 11 51 00 64",
                "08 00 81 00 11 51 00 7F",
                "09 00 81 00 11 07 00 64 00");
    }

    @Test
    @DisplayName("Sets the City Hub LED in color mode, then RGB mode, sending each mode's setup once")
    void shouldSetupTheLedModeBeforeTheColorAndRgbWrites() {
        // Given
        port.connect(0);

        // When
        port.setLedColor(9);
        port.setLedColor(3);
        port.setLedRgb(10, 20, 300);
        port.setLedRgb(1, 2, 3);
        port.setLedColor(6);

        // Then
        assertThat(radio.frames()).containsExactly(
                "0A 00 41 32 00 01 00 00 00 00",
                "08 00 81 32 11 51 00 09",
                "08 00 81 32 11 51 00 03",
                "0A 00 41 32 01 01 00 00 00 00",
                "0A 00 81 32 11 51 01 0A 14 2C",
                "0A 00 81 32 11 51 01 01 02 03",
                "0A 00 41 32 00 01 00 00 00 00",
                "08 00 81 32 11 51 00 06");
    }

    @Test
    @DisplayName("Subscribes a port to a mode and reads its value once a notification carries it")
    void shouldDecodeASingleValueReport() {
        // Given
        port.connect(0);

        // When & Then
        port.enableSensor(0, 2);
        assertThat(radio.frames()).containsExactly("0A 00 41 00 02 01 00 00 00 01");
        assertThat(port.readSensor(0)).isZero();

        radio.notify(0x08, 0x00, 0x45, 0x00, 0x68, 0x01, 0x00, 0x00);
        assertThat(port.readSensor(0)).isEqualTo(360);

        radio.notify(0x08, 0x00, 0x45, 0x00, 0xA6, 0xFF, 0xFF, 0xFF);
        assertThat(port.readSensor(0)).isEqualTo(-90);
    }

    @Test
    @DisplayName("Ignores a Hub Attached I/O notification as a value and reads a two-byte length")
    void shouldSkipAttachedIoAndReadTwoByteLengths() {
        // Given
        port.connect(0);
        port.enableSensor(0, 2);
        radio.notify(0x08, 0x00, 0x45, 0x00, 0x2C, 0x01, 0x00, 0x00);
        assertThat(port.readSensor(0)).isEqualTo(300);

        // When
        radio.notify(0x0F, 0x00, 0x04, 0x00, 0x01, 0x2E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        // A length of 128 or more takes two bytes (7 bits each); readers must skip both.
        radio.notify(0x89, 0x00, 0x00, 0x45, 0x00, 0x10, 0x00, 0x00, 0x00);

        // Then
        assertThat(port.readSensor(0)).isEqualTo(16);
        assertThat(port.portDevice(0)).isEqualTo(0x2E);
    }

    @Test
    @DisplayName("Reads int8 and int16 values and ignores reports of ports nobody enabled")
    void shouldDecodeInt8AndInt16Reports() {
        // Given
        port.connect(0);

        // When
        port.enableSensor(1, 0);
        radio.notify(0x05, 0x00, 0x45, 0x01, 0xFF);
        radio.notify(0x06, 0x00, 0x45, 0x02, 0x2C, 0x01);

        // Then
        assertThat(port.readSensor(1)).isEqualTo(-1);
        assertThat(port.readSensor(2)).isZero();

        port.enableSensor(2, 0);
        radio.notify(0x06, 0x00, 0x45, 0x02, 0x2C, 0x01);
        assertThat(port.readSensor(2)).isEqualTo(300);
    }

    @Test
    @DisplayName("Refuses a ninth sensor port without a write, and re-enabling a port reuses its slot")
    void shouldRefuseANinthSensorPort() {
        // Given
        port.connect(0);
        for (int sensor = 0; sensor < 8; sensor++) {
            port.enableSensor(sensor, 0);
        }
        int writes = radio.frames().size();

        // When
        port.enableSensor(8, 0);
        port.enableSensor(0, 1);

        // Then
        assertThat(radio.frames()).hasSize(writes + 1);
        assertThat(radio.frames().get(writes)).isEqualTo("0A 00 41 00 01 01 00 00 00 01");
    }

    @Test
    @DisplayName("Decodes multi-value reports into values of 1, 2 and 4 bytes and counts every report")
    void shouldDecodeMultiValueReports() {
        // Given
        port.connect(0);
        port.enableSensor(0x62, 0);
        assertThat(port.sensorReportSize(0x62)).isZero();
        assertThat(port.readSensorValue(0x62, 0, 2)).isZero();

        // When
        radio.notify(0x0A, 0x00, 0x45, 0x62, 0x34, 0x12, 0xFE, 0xFF, 0x0A, 0x00);
        port.readSensor(0x62);

        // Then
        assertThat(port.sensorReportSize(0x62)).isEqualTo(6);
        assertThat(port.readSensorValue(0x62, 0, 2)).isEqualTo(0x1234);
        assertThat(port.readSensorValue(0x62, 1, 2)).isEqualTo(-2);
        assertThat(port.readSensorValue(0x62, 2, 2)).isEqualTo(10);
        assertThat(port.sensorReportCount(0x62)).isEqualTo(1);

        radio.notify(0x0A, 0x00, 0x45, 0x62, 0x34, 0x12, 0xFE, 0xFF, 0x0A, 0x00);
        assertThat(port.sensorReportCount(0x62)).as("an unchanged report still counts").isEqualTo(2);
        assertThat(port.sensorReportCount(0x05)).isZero();
        assertThat(port.readSensorValue(0x62, 3, 2)).as("beyond the report").isZero();
        assertThat(port.readSensorValue(0x62, 0, 4)).isEqualTo(0xFFFE1234);
        assertThat(port.readSensorValue(0x62, 5, 1)).isZero();
        assertThat(port.readSensorValue(0x62, 0, 3)).as("widths are 1, 2 or 4").isZero();
        assertThat(port.readSensorValue(0x05, 0, 2)).as("a port nobody enabled").isZero();
    }

    @Test
    @DisplayName("Asks for the hub properties once, then answers from the notifications")
    void shouldRequestPropertiesOnceAndAnswerFromTheUpdates() {
        // Given
        port.connect(0);
        radio.notify(0x06, 0x00, 0x01, 0x06, 0x06, 0x55);
        radio.notify(0x09, 0x00, 0x01, 0x03, 0x06, 0x40, 0x00, 0x23, 0x10);
        radio.notify(0x09, 0x00, 0x01, 0x04, 0x06, 0x01, 0x00, 0x00, 0x10);
        radio.notify(0x08, 0x00, 0x01, 0x01, 0x06, 'H', 'u', 'b');
        radio.notify(0x06, 0x00, 0x01, 0x02, 0x06, 0x01);
        radio.notify(0x06, 0x00, 0x01, 0x05, 0x06, 0xC4);

        // When & Then
        assertThat(port.batteryPercent()).isEqualTo(85);
        assertThat(radio.frames()).containsExactly(
                "05 00 01 02 02",
                "05 00 01 05 02",
                "05 00 01 06 02",
                "05 00 01 01 05",
                "05 00 01 03 05",
                "05 00 01 04 05");
        assertThat(port.firmwareVersion()).isEqualTo(0x10230040);
        assertThat(port.hardwareVersion()).isEqualTo(0x10000001);
        assertThat(port.buttonPressed()).isTrue();
        assertThat(port.rssi()).isEqualTo(-60);

        byte[] name = new byte[8];
        assertThat(port.hubName(name, 8)).isEqualTo(3);
        assertThat(new String(name, 0, 3)).isEqualTo("Hub");
        assertThat(port.hubName(name, 2)).isEqualTo(2);
        assertThat(radio.frames()).as("requested once, not on every call").hasSize(6);
    }

    @Test
    @DisplayName("Switches the hub off and then drops every command after a disconnect")
    void shouldSwitchOffAndDropCommandsAfterDisconnecting() {
        // Given
        port.connect(0);

        // When
        port.switchOff();
        port.disconnect();
        port.setMotorPower(0, 50);

        // Then
        assertThat(radio.frames()).containsExactly("04 00 02 01");
        assertThat(port.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Pairs two motors into a virtual port, drives it, unlinks it and finds pairs in either order")
    void shouldLinkDriveAndUnlinkVirtualPorts() {
        // Given
        port.connect(0);
        radio.notify(0x09, 0x00, 0x04, 0x10, 0x02, 0x46, 0x00, 0x00, 0x01);

        // When & Then
        assertThat(port.linkMotors(0, 1)).isEqualTo(0x10);
        assertThat(radio.frames()).containsExactly("06 00 61 01 00 01");
        assertThat(port.linkMotors(1, 0)).isEqualTo(0x10);
        assertThat(radio.frames()).as("the pair is already linked").hasSize(1);

        port.setLinkedMotorPower(0x10, 50, -150);
        assertThat(radio.frames().get(1)).isEqualTo("08 00 81 10 11 02 32 9C");
        port.brakeLinkedMotors(0x10);
        assertThat(radio.frames().get(2)).isEqualTo("08 00 81 10 11 02 7F 7F");
        port.unlinkMotors(0x10);
        assertThat(radio.frames().get(3)).isEqualTo("05 00 61 00 10");

        radio.notify(0x09, 0x00, 0x04, 0x11, 0x02, 0x46, 0x00, 0x02, 0x03);
        assertThat(port.linkMotors(3, 2)).isEqualTo(0x11);
        assertThat(radio.frames().get(4)).isEqualTo("06 00 61 01 03 02");
    }

    @Test
    @DisplayName("A reconnect starts with no port reporting a value")
    void shouldStartAReconnectWithNoPortReporting() {
        // Given
        port.connect(0);
        port.enableSensor(2, 0);
        radio.notify(0x06, 0x00, 0x45, 0x02, 0x2C, 0x01);
        assertThat(port.readSensor(2)).isEqualTo(300);
        port.disconnect();

        // When
        boolean reconnected = port.connect(0);

        // Then
        assertThat(reconnected).isTrue();
        assertThat(port.readSensor(2)).isZero();
        radio.notify(0x06, 0x00, 0x45, 0x02, 0x2C, 0x01);
        assertThat(port.readSensor(2)).as("a port that was not enabled since reconnecting").isZero();
    }

    @Test
    @DisplayName("Answers nothing and sends nothing before connecting")
    void shouldAnswerNothingBeforeConnecting() {
        // When & Then
        port.enableSensor(0, 2);
        assertThat(port.readSensor(0)).isZero();
        assertThat(port.batteryPercent()).isEqualTo(-1);
        assertThat(port.linkMotors(0, 1)).isEqualTo(-1);
        assertThat(radio.frames()).isEmpty();
    }

    @Test
    @DisplayName("Connecting while connected keeps the link and does not reconnect")
    void shouldNotReconnectWhenAlreadyConnected() {
        // Given
        port.connect(0);

        // When & Then
        assertThat(port.connect(0)).isTrue();
        assertThat(radio.connects()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reports no hub type when the manufacturer data is not LEGO's or is too short")
    void shouldReportNoTypeForAForeignOrShortAdvertisement() {
        // Given
        radio.setAdvertisement(0x00, 0x00, 0x00, 0x41);

        // When
        port.connect(0);

        // Then
        assertThat(port.hubType()).isZero();

        // Given
        port.disconnect();
        radio.setAdvertisement(0x97, 0x03);

        // When
        port.connect(0);

        // Then
        assertThat(port.hubType()).isZero();
    }

    @Test
    @DisplayName("Uses the LED port of each hub type: Duplo on 0x11, Remote on 0x34, others on 0x32")
    void shouldUseTheLedPortOfEachHubType() {
        // Given
        radio.setAdvertisement(0x97, 0x03, 0x00, 0x20);
        port.connect(0);

        // When
        port.setLedColor(1);

        // Then
        assertThat(radio.frames()).containsExactly(
                "0A 00 41 11 00 01 00 00 00 00",
                "08 00 81 11 11 51 00 01");

        // Given
        port.disconnect();
        radio.setAdvertisement(0x97, 0x03, 0x00, 0x42);
        port.connect(0);

        // When
        port.setLedColor(2);

        // Then
        assertThat(radio.frames().get(2)).isEqualTo("0A 00 41 34 00 01 00 00 00 00");
        assertThat(radio.frames().get(3)).isEqualTo("08 00 81 34 11 51 00 02");
    }

    @Test
    @DisplayName("Reads a released button and a hub with no value for an unknown property")
    void shouldReadAReleasedButtonAndIgnoreAnUnknownProperty() {
        // Given
        port.connect(0);
        radio.notify(0x06, 0x00, 0x01, 0x02, 0x06, 0x00);
        radio.notify(0x06, 0x00, 0x01, 0x07, 0x06, 0x01);
        radio.notify(0x05, 0x00, 0x01, 0x05, 0x06);

        // When & Then
        assertThat(port.buttonPressed()).isFalse();
        assertThat(port.rssi()).isZero();
        assertThat(port.firmwareVersion()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Keeps only the first 20 bytes of a longer hub name and skips a firmware update that is too short")
    void shouldBoundTheNameAndSkipAShortFirmwareUpdate() {
        // Given
        port.connect(0);
        radio.notify(0x1E, 0x00, 0x01, 0x01, 0x06, 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A',
                'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A');
        radio.notify(0x07, 0x00, 0x01, 0x03, 0x06, 0x40, 0x00);

        // When & Then
        byte[] name = new byte[32];
        assertThat(port.hubName(name, 32)).isEqualTo(20);
        assertThat(port.hubName(new byte[2], 8)).isEqualTo(2);
        assertThat(port.hubName(name, 0)).isZero();
    }

    @Test
    @DisplayName("Decodes a one-byte value from a report of three bytes and ignores a report too short to carry one")
    void shouldDecodeOddSizedReports() {
        // Given
        port.connect(0);
        port.enableSensor(0, 0);

        // When
        radio.notify(0x07, 0x00, 0x45, 0x00, 0x01, 0x02, 0x03);

        // Then
        assertThat(port.readSensor(0)).isEqualTo(1);
        assertThat(port.readSensorValue(0, -1, 1)).isZero();

        // When
        radio.notify(0x04, 0x00, 0x45, 0x00);

        // Then
        assertThat(port.readSensor(0)).as("a message with no value byte is not a report").isEqualTo(1);
    }

    @Test
    @DisplayName("Ignores a message too short for its type and a message of an unknown type")
    void shouldIgnoreShortAndUnknownMessages() {
        // Given
        port.connect(0);
        port.enableSensor(0, 0);

        // When
        radio.notify(0x03, 0x00, 0x45);
        radio.notify(0x04, 0x00, 0x33, 0x00);

        // Then
        assertThat(port.readSensor(0)).isZero();
        assertThat(port.sensorReportSize(0x09)).isZero();
    }

    @Test
    @DisplayName("Records a device type only when the attached message carries it and its port is a motor port")
    void shouldRecordAttachedDevicesOnlyWhenComplete() {
        // Given
        port.connect(0);

        // When
        radio.notify(0x06, 0x00, 0x04, 0x00, 0x01, 0x2E);
        radio.notify(0x09, 0x00, 0x04, 0x05, 0x01, 0x2E, 0x00, 0x00, 0x00, 0x00, 0x00);
        radio.notify(0x09, 0x00, 0x04, 0x01, 0x01, 0x2E, 0x00, 0x00, 0x00);

        // Then
        assertThat(port.portDevice(0)).as("too short for a device type").isZero();
        assertThat(port.portDevice(1)).isEqualTo(0x2E);
        assertThat(port.portDevice(4)).as("not a motor port").isZero();
        assertThat(port.portDevice(-1)).isZero();
    }

    @Test
    @DisplayName("Forgets a virtual port the hub removes, and times out a link the hub never announces")
    void shouldForgetARemovedVirtualPortAndTimeOutAMissingOne() {
        // Given
        port.connect(0);
        radio.notify(0x09, 0x00, 0x04, 0x10, 0x02, 0x46, 0x00, 0x00, 0x01);
        assertThat(port.linkMotors(0, 1)).isEqualTo(0x10);
        radio.notify(0x05, 0x00, 0x04, 0x10, 0x00);
        port.portDevice(0);

        // When
        int relinked = port.linkMotors(0, 1);

        // Then
        assertThat(relinked).as("the removed port is no longer linked, and the hub never answers").isEqualTo(-1);
        assertThat(radio.frames()).last().isEqualTo("06 00 61 01 00 01");
    }

    @Test
    @DisplayName("Keeps only the first four virtual ports the hub announces")
    void shouldKeepOnlyTheFirstFourVirtualPorts() {
        // Given
        port.connect(0);
        radio.notify(0x09, 0x00, 0x04, 0x10, 0x02, 0x46, 0x00, 0x00, 0x01);
        radio.notify(0x09, 0x00, 0x04, 0x11, 0x02, 0x46, 0x00, 0x01, 0x02);
        radio.notify(0x09, 0x00, 0x04, 0x12, 0x02, 0x46, 0x00, 0x02, 0x03);
        radio.notify(0x09, 0x00, 0x04, 0x13, 0x02, 0x46, 0x00, 0x03, 0x01);
        radio.notify(0x09, 0x00, 0x04, 0x14, 0x02, 0x46, 0x00, 0x00, 0x02);

        // When & Then
        assertThat(port.linkMotors(0, 1)).isEqualTo(0x10);
        assertThat(port.linkMotors(1, 2)).isEqualTo(0x11);
        assertThat(port.linkMotors(2, 3)).isEqualTo(0x12);
        assertThat(port.linkMotors(3, 1)).isEqualTo(0x13);
        assertThat(port.linkMotors(0, 2)).as("the fifth announcement had no slot to keep it").isEqualTo(-1);
        assertThat(radio.frames()).last().isEqualTo("06 00 61 01 00 02");
    }

    @Test
    @DisplayName("Asks for the properties once and gives up waiting when the hub never answers")
    void shouldGiveUpWaitingForPropertiesWhenTheHubIsSilent() {
        // Given
        port.connect(0);

        // When
        int battery = port.batteryPercent();

        // Then
        assertThat(battery).isEqualTo(-1);
        assertThat(radio.frames()).hasSize(6);
        assertThat(port.hardwareVersion()).isEqualTo(-1);
        assertThat(radio.frames()).as("requested once").hasSize(6);
    }

    @Test
    @DisplayName("Reads no sensor value outside the report and no report of a port nobody enabled")
    void shouldReadNothingOutsideTheReport() {
        // Given
        port.connect(0);
        port.enableSensor(0, 2);
        radio.notify(0x06, 0x00, 0x45, 0x00, 0x2C, 0x01);

        // When & Then
        assertThat(port.readSensorValue(0, 0, 4)).as("the report is too short for a 4-byte value").isZero();
        assertThat(port.sensorReportSize(0x09)).isZero();
        assertThat(port.readSensorValue(0, 0, 2)).isEqualTo(300);
    }
}
