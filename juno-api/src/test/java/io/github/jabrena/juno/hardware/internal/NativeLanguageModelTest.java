package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.ModelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Native language model")
class NativeLanguageModelTest {

    @Test
    @DisplayName("Maps every engine result code to its status")
    void shouldMapEveryEngineResultCode() {
        // When & Then: the codes the C++ engine returns, in juno_language_model_load and juno_lm_ask
        assertThat(NativeLanguageModel.status(0)).isEqualTo(ModelStatus.OK);
        assertThat(NativeLanguageModel.status(-1)).isEqualTo(ModelStatus.INVALID_HANDLE);
        assertThat(NativeLanguageModel.status(-2)).isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(NativeLanguageModel.status(-3)).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(NativeLanguageModel.status(-4)).isEqualTo(ModelStatus.NOT_LOADED);
        assertThat(NativeLanguageModel.status(-5)).isEqualTo(ModelStatus.INVALID_ARGUMENT);
    }
}
