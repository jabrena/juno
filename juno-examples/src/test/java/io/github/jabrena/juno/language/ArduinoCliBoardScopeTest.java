package io.github.jabrena.juno.language;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArduinoCliBoardScopeTest {

    @Test
    void allUsesEveryBoardForAnUnannotatedExample() {
        assertThat(ArduinoCliBoardScope.ALL.genericBoards()).containsExactly(Board.values());
    }

    @Test
    void representativeUsesUnoR4WifiForAnUnannotatedExample() {
        assertThat(ArduinoCliBoardScope.REPRESENTATIVE.genericBoards()).containsExactly(Board.UNO_R4_WIFI);
    }

    @ParameterizedTest
    @CsvSource({
        "all, ALL",
        "' representative ', REPRESENTATIVE"
    })
    void parsesSupportedValues(String configured, ArduinoCliBoardScope expected) {
        assertThat(ArduinoCliBoardScope.parse(configured)).isEqualTo(expected);
    }

    @Test
    void rejectsAnUnsupportedValue() {
        assertThatThrownBy(() -> ArduinoCliBoardScope.parse("uno-r3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported juno.arduinoCliBoardScope 'uno-r3'; expected one of: all, representative");
    }
}
