package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardListJsonParserTest {
    @Test
    void rejectsMalformedBoardListJson() {
        assertThatThrownBy(() -> BoardListJsonParser.parse("{}"))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("missing detected_ports array");
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

        var ports = BoardListJsonParser.parse(json);

        assertThat(ports).containsExactly(
                new BoardListJsonParser.DetectedPort("/dev/cu.usbmodem1", "serial",
                        java.util.List.of("Arduino UNO R4 WiFi"), java.util.List.of("arduino:renesas_uno:unor4wifi")),
                new BoardListJsonParser.DetectedPort("/dev/cu.Bluetooth", "serial",
                        java.util.List.of(), java.util.List.of()));
        assertThat(BoardsMojo.describe(ports.getFirst()))
                .isEqualTo("  /dev/cu.usbmodem1 [serial] - Arduino UNO R4 WiFi (arduino:renesas_uno:unor4wifi)");
        assertThat(BoardsMojo.describe(ports.getLast())).isEqualTo("  /dev/cu.Bluetooth [serial] - unknown board");
    }
}
