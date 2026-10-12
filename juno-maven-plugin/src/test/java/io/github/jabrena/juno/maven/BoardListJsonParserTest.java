package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardListJsonParserTest {
    private final BoardListJsonParser parser = new BoardListJsonParser();

    @Test
    void rejectsMalformedBoardListJson() {
        assertThatThrownBy(() -> parser.parse("{}"))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("missing detected_ports array");
    }

    @Test
    void rejectsUnterminatedPortArray() {
        assertThatThrownBy(() -> parser.parse("""
                {"detected_ports": [{"port": {"address": "COM3"}}"""))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("unterminated detected_ports array");
    }

    @Test
    void parsesAddressProtocolAndMatchingBoards() {
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

        List<DetectedPort> ports = parser.parse(json);

        assertThat(ports).containsExactly(
                new DetectedPort("/dev/cu.usbmodem1", "serial",
                        List.of("Arduino UNO R4 WiFi"), List.of("arduino:renesas_uno:unor4wifi")),
                new DetectedPort("/dev/cu.Bluetooth", "serial", List.of(), List.of()));
    }

    @Test
    void unescapesJsonStrings() {
        List<DetectedPort> ports = parser.parse("""
                {"detected_ports": [{"port": {"address": "C:\\\\COM3 \\u00e9"}}]}
                """);

        assertThat(ports).extracting(DetectedPort::address).containsExactly("C:\\COM3 é");
    }
}
