package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Native language model")
class NativeLanguageModelTest {

    @Test
    @DisplayName("Maps every engine result code to its status")
    void shouldMapEveryEngineResultCode() {
        // When & Then: the codes the C++ engine returns, in juno_lm_load_bytes and juno_lm_ask
        assertThat(NativeLanguageModel.status(0)).isEqualTo(ModelStatus.OK);
        assertThat(NativeLanguageModel.status(-1)).isEqualTo(ModelStatus.INVALID_HANDLE);
        assertThat(NativeLanguageModel.status(-2)).isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(NativeLanguageModel.status(-3)).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(NativeLanguageModel.status(-4)).isEqualTo(ModelStatus.NOT_LOADED);
        assertThat(NativeLanguageModel.status(-5)).isEqualTo(ModelStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("A missing or empty model file on the card is unreadable, without touching PSRAM")
    void shouldReportAMissingModelFileAsUnreadable() {
        // Given
        SdCard missing = card(-1);
        SdCard empty = card(0);

        // When & Then
        assertThat(NativeLanguageModel.languageModel().begin(missing, "/JUNO.BIN")).isEqualTo(ModelStatus.UNREADABLE);
        assertThat(NativeLanguageModel.languageModel().begin(empty, "/JUNO.BIN")).isEqualTo(ModelStatus.UNREADABLE);
    }

    @Test
    @DisplayName("A model file on the card goes to a PSRAM buffer, and the bundled model to the engine")
    void shouldStageAModelFileInPsram() {
        // When & Then: off the board the natives are absent, so reaching them proves which path each begin takes
        assertThatThrownBy(() -> NativeLanguageModel.languageModel().begin(card(381_808), "/JUNO.BIN"))
                .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining("allocate");
        assertThatThrownBy(() -> NativeLanguageModel.languageModel().begin())
                .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining("beginDefault");
    }

    @Test
    @DisplayName("Forwards runtime operations to the native engine")
    void shouldForwardRuntimeOperationsToNativeEngine() {
        // The native entry points are unavailable on the desktop JVM; reaching each one still verifies the Java
        // adapter invokes the board boundary rather than silently implementing a different behavior.
        assertThatThrownBy(() -> NativeLanguageModel.languageModel().isLoaded())
                .isInstanceOf(UnsatisfiedLinkError.class);
        assertThatThrownBy(() -> NativeLanguageModel.languageModel().width())
                .isInstanceOf(UnsatisfiedLinkError.class);
        assertThatThrownBy(() -> NativeLanguageModel.languageModel().layers())
                .isInstanceOf(UnsatisfiedLinkError.class);
        assertThatThrownBy(() -> NativeLanguageModel.languageModel().ask("hello", 1))
                .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining("askText");
        assertThatThrownBy(() -> NativeLanguageModel.languageModel().ask(new byte[] {'h'}, 1, 1))
                .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining("askBytes");
        assertThatThrownBy(() -> NativeLanguageModel.languageModel().nextChar())
                .isInstanceOf(UnsatisfiedLinkError.class);
        assertThatThrownBy(() -> NativeLanguageModel.languageModel().release())
                .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining("releaseModel");
    }

    @Test
    @DisplayName("Copies a model file chunk by chunk, and rejects a missing or short one")
    void shouldCopyChunkByChunk() throws Exception {
        // Given
        byte[] model = new byte[2500];
        for (int i = 0; i < model.length; i++) {
            model[i] = (byte) i;
        }
        java.io.ByteArrayOutputStream copied = new java.io.ByteArrayOutputStream();
        java.util.List<Integer> offsets = new java.util.ArrayList<>();
        NativeLanguageModel.ChunkSink sink = (offset, chunk, length) -> {
            offsets.add(offset);
            copied.write(chunk, 0, length);
        };

        // When
        boolean complete = NativeLanguageModel.copy(new java.io.ByteArrayInputStream(model), model.length, sink);

        // Then
        assertThat(complete).isTrue();
        assertThat(copied.toByteArray()).isEqualTo(model);
        assertThat(offsets).containsExactly(0, 1024, 2048);
        assertThat(NativeLanguageModel.copy(null, 10, sink)).isFalse();
        assertThat(NativeLanguageModel.copy(new java.io.ByteArrayInputStream(new byte[5]), 10, sink)).isFalse();
    }

    /** A card whose files all have {@code size} bytes; every other call answers its type's default. */
    private static SdCard card(int size) {
        return (SdCard) Proxy.newProxyInstance(SdCard.class.getClassLoader(), new Class<?>[] {SdCard.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "size" -> size;
                    case "begin", "exists" -> true;
                    default -> null;
                });
    }
}
