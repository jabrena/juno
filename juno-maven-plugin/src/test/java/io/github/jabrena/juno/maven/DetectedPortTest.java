package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.board.Board;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("Detected serial port")
class DetectedPortTest {
    private static final String UNO_R4_WIFI = Board.UNO_R4_WIFI.fqbn();

    @Test
    @DisplayName("Describes a recognized board and hosts its FQBN")
    void shouldDescribeARecognizedBoardAndHostItsFqbn() {
        // Given
        DetectedPort port = new DetectedPort("/dev/cu.usbmodem1", "serial", List.of("Arduino UNO R4 WiFi"),
                List.of(UNO_R4_WIFI));

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
        DetectedPort port = new DetectedPort("/dev/cu.Bluetooth", "", List.of(), List.of());

        // When
        String description = port.describe();

        // Then
        assertThat(description).isEqualTo("  /dev/cu.Bluetooth - unknown board");
        assertThat(port.hosts(UNO_R4_WIFI)).isFalse();
    }
}
