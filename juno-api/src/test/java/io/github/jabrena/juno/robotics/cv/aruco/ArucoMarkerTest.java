package io.github.jabrena.juno.robotics.cv.aruco;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("ArUco DICT_4X4_50 marker")
class ArucoMarkerTest {

    @Test
    @DisplayName("id 0 matches the OpenCV DICT_4X4_50 marker, with a black border")
    void id0MatchesTheDictionary() {
        String[] rows = {
            "000000",
            "010110",
            "001010",
            "000110",
            "000100",
            "000000"
        };
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 6; col++) {
                assertThat(ArucoMarker.isWhite(0, row, col))
                        .as("cell %d,%d", row, col)
                        .isEqualTo(rows[row].charAt(col) == '1');
            }
        }
    }

    @Test
    @DisplayName("every marker has a black border")
    void everyBorderIsBlack() {
        for (int id = 0; id < ArucoMarker.MARKERS; id++) {
            for (int i = 0; i < 6; i++) {
                assertThat(ArucoMarker.isWhite(id, 0, i)).isFalse();
                assertThat(ArucoMarker.isWhite(id, 5, i)).isFalse();
                assertThat(ArucoMarker.isWhite(id, i, 0)).isFalse();
                assertThat(ArucoMarker.isWhite(id, i, 5)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("ids outside the dictionary are rejected")
    void rejectsIdsOutsideTheDictionary() {
        assertThatThrownBy(() -> ArucoMarker.isWhite(-1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArucoMarker.isWhite(50, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("draw rejects an id outside the dictionary before painting anything")
    void drawRejectsBadIds() {
        TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
        assertThatThrownBy(() -> ArucoMarker.draw(tft, 50, 0, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
