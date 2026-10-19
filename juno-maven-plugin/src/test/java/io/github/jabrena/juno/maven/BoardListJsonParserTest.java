package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Arduino CLI board list JSON parsing")
class BoardListJsonParserTest {
    private final BoardListJsonParser parser = new BoardListJsonParser();

    @Test
    @DisplayName("Rejects board list JSON without a detected_ports array")
    void shouldRejectBoardListJsonWithoutADetectedPortsArray() {
        // Given
        String json = "{}";

        // When & Then
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("missing detected_ports array");
    }

    @Test
    @DisplayName("Rejects an unterminated detected_ports array")
    void shouldRejectAnUnterminatedPortArray() {
        // Given
        String json = """
                {"detected_ports": [{"port": {"address": "COM3"}}""";

        // When & Then
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("unterminated detected_ports array");
    }

    @Test
    @DisplayName("Parses the address, protocol and matching boards of each port")
    void shouldParseAddressProtocolAndMatchingBoardsOfEachPort() {
        // Given
        String json = """
                {
                  "detected_ports": [
                    {
                      "matching_boards": [
                        {"name": "Arduino UNO R4 WiFi", "fqbn": "arduino:renesas_uno:unor4wifi"}
                      ],
                      "port": {"address": "/dev/cu.usbmodem1", "protocol": "serial"}
                    },
                    {
                      "port": {"address": "/dev/cu.Bluetooth", "protocol": "serial"}
                    }
                  ]
                }
                """;

        // When
        List<DetectedPort> ports = parser.parse(json);

        // Then
        assertThat(ports).containsExactly(
                new DetectedPort("/dev/cu.usbmodem1", "serial",
                        List.of("Arduino " + Board.UNO_R4_WIFI.displayName()), List.of(Board.UNO_R4_WIFI.fqbn())),
                new DetectedPort("/dev/cu.Bluetooth", "serial", List.of(), List.of()));
    }

    @Test
    @DisplayName("Unescapes JSON string values")
    void shouldUnescapeJsonStringValues() {
        // Given
        String json = """
                {"detected_ports": [{"port": {"address": "C:\\\\COM3 \\u00e9"}}]}
                """;

        // When
        List<DetectedPort> ports = parser.parse(json);

        // Then
        assertThat(ports).extracting(DetectedPort::address).containsExactly("C:\\COM3 é");
    }
}
