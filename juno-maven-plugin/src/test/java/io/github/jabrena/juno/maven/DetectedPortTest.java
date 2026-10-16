package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DetectedPortTest {
    private static final String UNO_R4_WIFI = Board.UNO_R4_WIFI.fqbn();

    @Test
    void describesARecognizedBoard() {
        DetectedPort port = new DetectedPort("/dev/cu.usbmodem1", "serial", List.of("Arduino UNO R4 WiFi"),
                List.of(UNO_R4_WIFI));

        assertThat(port.describe())
                .isEqualTo("  /dev/cu.usbmodem1 [serial] - Arduino UNO R4 WiFi (" + UNO_R4_WIFI + ")");
        assertThat(port.hosts(UNO_R4_WIFI)).isTrue();
    }

    @Test
    void describesAnUnknownBoardWithoutProtocol() {
        DetectedPort port = new DetectedPort("/dev/cu.Bluetooth", "", List.of(), List.of());

        assertThat(port.describe()).isEqualTo("  /dev/cu.Bluetooth - unknown board");
        assertThat(port.hosts(UNO_R4_WIFI)).isFalse();
    }
}
