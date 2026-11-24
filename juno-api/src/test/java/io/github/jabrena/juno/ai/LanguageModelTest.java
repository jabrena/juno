package io.github.jabrena.juno.ai;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.internal.NativeLanguageModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Language model")
class LanguageModelTest {

    @Test
    @DisplayName("The MCU facade hands out the one board engine")
    void shouldHandOutTheBoardEngine() {
        // When
        LanguageModel model = MCU.getLanguageModel();

        // Then
        assertThat(model).isSameAs(NativeLanguageModel.languageModel()).isInstanceOf(NativeLanguageModel.class);
    }

    @Test
    @DisplayName("Asking with bytes passes the array capacity to the native engine")
    void shouldPassTheArrayCapacityToTheNativeEngine() {
        // Given
        LanguageModel model = MCU.getLanguageModel();

        // When & Then: off the board the native engine is absent, so reaching it proves the wrapper delegates
        assertThatThrownBy(() -> model.ask(new byte[8], 3, 0)).isInstanceOf(UnsatisfiedLinkError.class)
                .hasMessageContaining("ask");
    }

    @Test
    @DisplayName("Releasing reaches the native engine")
    void shouldReleaseThroughTheNativeEngine() {
        // When & Then: off the board the native engine is absent, so reaching it proves the wrapper delegates
        assertThatThrownBy(() -> MCU.getLanguageModel().release()).isInstanceOf(UnsatisfiedLinkError.class)
                .hasMessageContaining("releaseModel");
    }

    @Test
    @DisplayName("Outcomes are a closed set, and END and the limits stay plain numbers")
    void shouldKeepOutcomesAsAnEnumAndLimitsAsNumbers() {
        // When & Then
        assertThat(ModelStatus.values()).containsExactly(ModelStatus.OK,
                ModelStatus.INVALID_HANDLE, ModelStatus.INVALID_MODEL, ModelStatus.EXHAUSTED,
                ModelStatus.NOT_LOADED, ModelStatus.INVALID_ARGUMENT);
        assertThat(LanguageModel.END).isNegative();
        assertThat(LanguageModel.MAX_QUESTION).isEqualTo(72);
        assertThat(LanguageModel.MAX_CREATIVITY).isEqualTo(9);
    }
}
