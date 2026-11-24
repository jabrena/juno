package io.github.jabrena.juno.ai;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.internal.JlmLanguageModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Language model")
class LanguageModelTest {

    @Test
    @DisplayName("The MCU facade hands out the one board engine")
    void shouldHandOutTheBoardEngine() {
        // When
        LanguageModel model = MCU.getLanguageModel();

        // Then
        assertThat(model).isSameAs(JlmLanguageModel.languageModel()).isInstanceOf(JlmLanguageModel.class);
    }

    @Test
    @DisplayName("Before begin, the engine reports no model, refuses questions and releases nothing")
    void shouldRefuseQuestionsBeforeBegin() {
        // Given
        LanguageModel model = MCU.getLanguageModel();

        // When
        model.release();

        // Then: nothing reaches the board's PSRAM, so this runs off the board too
        assertThat(model.isLoaded()).isFalse();
        assertThat(model.width()).isZero();
        assertThat(model.layers()).isZero();
        assertThat(model.ask("Hello", 0)).isEqualTo(ModelStatus.NOT_LOADED);
        assertThat(model.ask(new byte[8], 3, 0)).isEqualTo(ModelStatus.NOT_LOADED);
        assertThat(model.nextChar()).isEqualTo(LanguageModel.END);
    }

    @Test
    @DisplayName("Outcomes are a closed set, and END and the limits stay plain numbers")
    void shouldKeepOutcomesAsAnEnumAndLimitsAsNumbers() {
        // When & Then
        assertThat(ModelStatus.values()).containsExactly(ModelStatus.OK,
                ModelStatus.INVALID_HANDLE, ModelStatus.INVALID_MODEL, ModelStatus.EXHAUSTED,
                ModelStatus.NOT_LOADED, ModelStatus.INVALID_ARGUMENT, ModelStatus.UNREADABLE);
        assertThat(LanguageModel.END).isNegative();
        assertThat(LanguageModel.MAX_QUESTION).isEqualTo(72);
        assertThat(LanguageModel.MAX_CREATIVITY).isEqualTo(9);
    }
}
