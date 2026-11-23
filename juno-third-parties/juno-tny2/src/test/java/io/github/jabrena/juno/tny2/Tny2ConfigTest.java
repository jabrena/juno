package io.github.jabrena.juno.tny2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TNY2 config")
class Tny2ConfigTest {

    @Test
    @DisplayName("Sizes the TINY 2.1 shape exactly as its published TINY.BIN")
    void shouldSizeTheTiny21ShapeAsItsPublishedFile() {
        // When
        Tny2Config config = Tny2Config.tiny21();

        // Then
        assertThat(config).isEqualTo(new Tny2Config(96, 4, 4, 256));
        assertThat(config.headWidth()).isEqualTo(24);
        assertThat(config.parameterCount()).isEqualTo(366_528);
        assertThat(config.fileSize()).isEqualTo(381_808);
    }

    @Test
    @DisplayName("Accepts the limits of the board engine")
    void shouldAcceptTheLimitsOfTheBoardEngine() {
        // When & Then
        assertThatCode(() -> new Tny2Config(8, 1, 1, 8)).doesNotThrowAnyException();
        assertThatCode(() -> new Tny2Config(128, 8, 128, 512)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}x{1}x{2}x{3} fails on {4}")
    @CsvSource({
        "7, 1, 1, 8, width", "129, 1, 1, 8, width", "8, 0, 1, 8, layers", "8, 9, 1, 8, layers",
        "8, 1, 0, 8, heads", "8, 1, 3, 8, heads", "8, 1, 1, 7, hidden", "8, 1, 1, 513, hidden"})
    @DisplayName("Rejects dimensions the board engine refuses")
    void shouldRejectDimensionsTheBoardEngineRefuses(int width, int layers, int heads, int hidden, String field) {
        // When & Then
        assertThatThrownBy(() -> new Tny2Config(width, layers, heads, hidden))
                .isInstanceOf(Tny2FormatException.class).hasMessageStartingWith(field);
    }
}
