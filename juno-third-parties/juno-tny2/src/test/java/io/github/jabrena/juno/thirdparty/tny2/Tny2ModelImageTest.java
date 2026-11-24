package io.github.jabrena.juno.thirdparty.tny2;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.Tny2Inference;
import io.github.jabrena.juno.tny2.Tny2Reader;
import io.github.jabrena.juno.tny2.image.Tny2ImageSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Generated TNY2 model image")
class Tny2ModelImageTest {
    private static final Path MODEL = Path.of("src/main/resources/tny2/JUNO.BIN");

    @Test
    @DisplayName("Generated Java image matches the tracked JUNO.BIN, byte for byte")
    void shouldMatchTheTrackedModel() throws Exception {
        // Given
        byte[] model = Files.readAllBytes(MODEL);
        byte[] image = new byte[Tny2ModelImage.LENGTH];

        // When
        for (int i = 0; i < image.length; i++) {
            image[i] = Tny2ModelImage.byteAt(i);
        }

        // Then
        assertThat(image).isEqualTo(model);
        assertThat(Tny2ModelImage.CHECKSUM).isEqualTo(new Tny2ImageSource(model).checksum());
        Tny2Config config = new Tny2Reader().read(model).config();
        assertThat(config).isEqualTo(new Tny2Config(Tny2ModelImage.WIDTH, Tny2ModelImage.LAYERS,
                Tny2ModelImage.HEADS, Tny2ModelImage.HIDDEN));
        assertThat(Tny2ModelImage.byteAt(Tny2ModelImage.LENGTH + 4096)).isZero();
    }

    @Test
    @DisplayName("The tracked model answers the bundled Juno facts")
    void shouldAnswerTheBundledJunoFacts() throws Exception {
        // Given
        Tny2Inference inference = new Tny2Inference(new Tny2Reader().read(MODEL));

        // When & Then
        assertThat(inference.answer("What is Juno?"))
                .isEqualTo("Juno compiles Java programs into firmware for microcontrollers.");
        assertThat(inference.answer("How much PSRAM does the Nano ESP32 have?")).isEqualTo("8 MB.");
    }
}
