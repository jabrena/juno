package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardListJsonParserTest {
    @Test
    void rejectsMalformedBoardListJson() {
        assertThatThrownBy(() -> BoardListJsonParser.parse("{}"))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("missing detected_ports array");
    }
}
