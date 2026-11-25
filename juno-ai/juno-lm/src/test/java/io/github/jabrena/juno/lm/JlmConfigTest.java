package io.github.jabrena.juno.lm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JLM1 config")
class JlmConfigTest {

    @Test
    @DisplayName("Sizes the standard shape: 366,528 parameters behind a header with the 95-character alphabet")
    void shouldSizeTheStandardShape() {
        // When
        JlmConfig config = JlmConfig.standard();

        // Then
        assertThat(config).isEqualTo(new JlmConfig(96, 4, 4, 256, 128, JlmVocabulary.ASCII));
        assertThat(config.headWidth()).isEqualTo(24);
        assertThat(config.parameterCount()).isEqualTo(366_528);
        assertThat(config.headerSize()).isEqualTo(111);
        assertThat(config.fileSize()).isEqualTo(381_907);
    }

    @Test
    @DisplayName("Accepts the limits of the board engine")
    void shouldAcceptTheLimitsOfTheBoardEngine() {
        // When & Then
        assertThatCode(() -> new JlmConfig(8, 1, 1, 8)).doesNotThrowAnyException();
        assertThatCode(() -> new JlmConfig(128, 8, 128, 512)).doesNotThrowAnyException();
        assertThatCode(() -> new JlmConfig(8, 1, 1, 8, 74, new JlmVocabulary(" "))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rejects a context shorter than the longest question or longer than the engine holds")
    void shouldRejectAContextTheBoardEngineRefuses() {
        // When & Then
        assertThatThrownBy(() -> new JlmConfig(8, 1, 1, 8, 73, JlmVocabulary.ASCII))
                .isInstanceOf(JlmFormatException.class).hasMessage("context must be 74 to 128, not 73");
        assertThatThrownBy(() -> new JlmConfig(8, 1, 1, 8, 129, JlmVocabulary.ASCII))
                .isInstanceOf(JlmFormatException.class).hasMessageStartingWith("context");
    }

    @ParameterizedTest(name = "{0}x{1}x{2}x{3} fails on {4}")
    @CsvSource({
        "7, 1, 1, 8, width", "129, 1, 1, 8, width", "8, 0, 1, 8, layers", "8, 9, 1, 8, layers",
        "8, 1, 0, 8, heads", "8, 1, 3, 8, heads", "8, 1, 1, 7, hidden", "8, 1, 1, 513, hidden"})
    @DisplayName("Rejects dimensions the board engine refuses")
    void shouldRejectDimensionsTheBoardEngineRefuses(int width, int layers, int heads, int hidden, String field) {
        // When & Then
        assertThatThrownBy(() -> new JlmConfig(width, layers, heads, hidden))
                .isInstanceOf(JlmFormatException.class).hasMessageStartingWith(field);
    }
}
