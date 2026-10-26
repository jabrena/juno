package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Detected serial port")
class DetectedPortTest {
    private static final String UNO_R4_WIFI = Board.UNO_R4_WIFI.fqbn();

    @Test
    @DisplayName("Describes a recognized board and hosts its FQBN")
    void shouldDescribeARecognizedBoardAndHostItsFqbn() {
        // Given
        DetectedPort port = new DetectedPort("/dev/cu.usbmodem1", "serial", List.of("Arduino UNO R4 WiFi"),
                List.of(UNO_R4_WIFI), "0x2341");

        // When
        String description = port.describe();

        // Then
        assertThat(description)
                .isEqualTo("  /dev/cu.usbmodem1 [serial] - Arduino UNO R4 WiFi (" + UNO_R4_WIFI + ")");
        assertThat(port.hosts(UNO_R4_WIFI)).isTrue();
    }

    @Test
    @DisplayName("Describes an unknown board without a protocol and hosts no FQBN")
    void shouldDescribeAnUnknownBoardWithoutProtocolAndHostNoFqbn() {
        // Given
        DetectedPort port = new DetectedPort("/dev/cu.Bluetooth", "", List.of(), List.of(), "");

        // When
        String description = port.describe();

        // Then
        assertThat(description).isEqualTo("  /dev/cu.Bluetooth - unknown board");
        assertThat(port.hosts(UNO_R4_WIFI)).isFalse();
    }

    @Test
    @DisplayName("Recognizes an Espressif USB serial port as a Cardputer port")
    void shouldRecognizeAnEspressifUsbSerialPortAsACardputerPort() {
        // Given
        DetectedPort port = new DetectedPort("/dev/cu.usbmodem1201", "serial", List.of(), List.of(), "0x303A");

        // When & Then
        assertThat(port.hosts("m5stack:esp32:m5stack_cardputer")).isTrue();
        assertThat(port.hosts("m5stack:esp32:m5stack_cardputer:FlashSize=8M,PartitionScheme=default_8MB")).isTrue();
        assertThat(port.hosts(UNO_R4_WIFI)).isFalse();
    }
}
