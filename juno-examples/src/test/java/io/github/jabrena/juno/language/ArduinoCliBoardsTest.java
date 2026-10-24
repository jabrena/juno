package io.github.jabrena.juno.language;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArduinoCliBoardsTest {

    @Test
    void blankSelectionIncludesEveryBoard() {
        ArduinoCliBoards boards = ArduinoCliBoards.parse(" ");

        assertThat(Board.values()).allMatch(boards::includes);
    }

    @Test
    void includesOnlyTheSelectedBoards() {
        ArduinoCliBoards boards = ArduinoCliBoards.parse(" arduino-one-r3, arduino-one-r3-wifi-rev2 ");

        assertThat(boards.includes(Board.UNO_R3)).isTrue();
        assertThat(boards.includes(Board.UNO_R3_WIFI_REV2)).isTrue();
        assertThat(boards.includes(Board.UNO_R4_WIFI)).isFalse();
    }

    @Test
    void rejectsAnUnsupportedBoard() {
        assertThatThrownBy(() -> ArduinoCliBoards.parse("arduino-one-r4-wifi,unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported juno.arduinoCliBoards")
                .hasMessageContaining("expected comma-separated values from:");
    }

    @Test
    void rejectsAnEmptyBoardWithinTheSelection() {
        assertThatThrownBy(() -> ArduinoCliBoards.parse("arduino-one-r4-wifi,"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported juno.arduinoCliBoards");
    }
}
