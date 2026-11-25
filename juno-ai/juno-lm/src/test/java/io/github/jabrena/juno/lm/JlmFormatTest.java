package io.github.jabrena.juno.lm;

import io.github.jabrena.juno.lm.training.JlmNetwork;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JLM1 reader and writer")
class JlmFormatTest {
    private static final JlmConfig SMALL = new JlmConfig(8, 2, 2, 12);

    @TempDir
    private Path directory;

    @Test
    @DisplayName("Writes the header and reads back an identical model")
    void shouldWriteTheHeaderAndReadBackAnIdenticalModel() throws Exception {
        // Given
        JlmModel model = JlmNetwork.initialize(SMALL, new SplittableRandom(1)).quantize();
        Path file = directory.resolve("models/JUNO.BIN");

        // When
        new JlmWriter().write(model, file);
        JlmModel read = new JlmReader().read(file);

        // Then
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(Arrays.copyOf(bytes, 4)).containsExactly('J', 'L', 'M', '1');
        assertThat(List.of(header.getShort(4), header.getShort(6), header.getShort(8), header.getShort(10),
                header.getShort(12), header.getShort(14))).containsExactly((short) 8, (short) 2, (short) 2,
                (short) 12, (short) 128, (short) 97);
        assertThat(new String(bytes, 16, 95, StandardCharsets.US_ASCII)).isEqualTo(JlmVocabulary.ASCII.alphabet());
        assertThat(bytes).hasSize(SMALL.fileSize());
        assertThat(read).isEqualTo(model);
        assertThat(new JlmWriter().toBytes(read)).isEqualTo(bytes);
    }

    @Test
    @DisplayName("Writes tables as a little-endian float scale and the row's weights")
    void shouldWriteTablesRowByRow() {
        // Given
        JlmModel model = JlmNetwork.initialize(SMALL, new SplittableRandom(1)).quantize();

        // When
        ByteBuffer bytes = ByteBuffer.wrap(new JlmWriter().toBytes(model)).order(ByteOrder.LITTLE_ENDIAN);

        // Then
        int header = SMALL.headerSize();
        int secondRow = header + Float.BYTES + SMALL.width();
        assertThat(bytes.getFloat(header)).isEqualTo(model.embedding().scale(0));
        assertThat(bytes.get(header + Float.BYTES)).isEqualTo(model.embedding().weight(0, 0));
        assertThat(bytes.getFloat(secondRow)).isEqualTo(model.embedding().scale(1));
        assertThat(bytes.getFloat(bytes.capacity() - Float.BYTES)).isEqualTo(model.finalGain().get(7));
    }

    @Test
    @DisplayName("Rejects a file without the JLM1 magic")
    void shouldRejectAFileWithoutTheMagic() {
        // Given
        byte[] bytes = validBytes();
        bytes[3] = '2';

        // When & Then
        assertThatThrownBy(() -> new JlmReader().read(bytes)).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("magic");
    }

    @Test
    @DisplayName("Rejects a truncated file and one with bytes after the model")
    void shouldRejectATruncatedOrOverlongFile() {
        // Given
        byte[] bytes = validBytes();

        // When & Then
        assertThatThrownBy(() -> new JlmReader().read(Arrays.copyOf(bytes, bytes.length - 1)))
                .isInstanceOf(JlmFormatException.class).hasMessageContaining("truncated");
        assertThatThrownBy(() -> new JlmReader().read(Arrays.copyOf(bytes, 2)))
                .isInstanceOf(JlmFormatException.class).hasMessageContaining("truncated");
        assertThatThrownBy(() -> new JlmReader().read(Arrays.copyOf(bytes, bytes.length + 3)))
                .isInstanceOf(JlmFormatException.class).hasMessageContaining("3 bytes after the model");
    }

    @Test
    @DisplayName("Rejects dimensions the board engine refuses")
    void shouldRejectDimensionsTheBoardEngineRefuses() {
        // Given
        byte[] bytes = validBytes();
        bytes[6] = 9;

        // When & Then
        assertThatThrownBy(() -> new JlmReader().read(bytes)).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("layers");
    }

    @Test
    @DisplayName("Reads back a model with its own context and alphabet, which inference keeps to")
    void shouldReadBackACustomContextAndAlphabet() {
        // Given
        JlmConfig config = new JlmConfig(8, 1, 2, 8, 80, new JlmVocabulary(" abc"));
        JlmModel model = JlmNetwork.initialize(config, new SplittableRandom(3)).quantize();

        // When
        byte[] bytes = new JlmWriter().toBytes(model);
        JlmModel read = new JlmReader().read(bytes);
        String answer = new JlmInference(read, 0f).answer("abc", 9, new SplittableRandom(4));

        // Then
        assertThat(bytes).hasSize(config.fileSize());
        assertThat(new String(bytes, 16, 4, StandardCharsets.US_ASCII)).isEqualTo(" abc");
        assertThat(read).isEqualTo(model);
        assertThat(read.position().rows()).isEqualTo(80);
        assertThat(answer).hasSizeLessThanOrEqualTo(80 - 4).matches("[ abc]*");
    }

    @Test
    @DisplayName("Rejects a vocabulary without characters and an alphabet the engine refuses")
    void shouldRejectAVocabularyTheEngineRefuses() {
        // Given
        byte[] bytes = validBytes();
        byte[] empty = bytes.clone();
        empty[14] = 2;
        byte[] repeated = bytes.clone();
        repeated[17] = ' ';

        // When & Then
        assertThatThrownBy(() -> new JlmReader().read(empty)).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("more than 2 tokens");
        assertThatThrownBy(() -> new JlmReader().read(repeated)).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("repeats");
    }

    @Test
    @DisplayName("Rejects a model whose tables do not match its header")
    void shouldRejectAModelWhoseTablesDoNotMatchItsHeader() {
        // Given
        JlmModel model = JlmNetwork.initialize(SMALL, new SplittableRandom(1)).quantize();
        JlmLayer layer = model.layers().getFirst();
        GainVector shortGain = new GainVector(new float[] {1f});

        // When & Then
        assertThatThrownBy(() -> new JlmModel(SMALL, model.embedding(), model.position(), List.of(layer),
                model.finalGain())).isInstanceOf(JlmFormatException.class).hasMessageContaining("2 layers");
        assertThatThrownBy(() -> new JlmModel(SMALL, model.position(), model.position(), model.layers(),
                model.finalGain())).isInstanceOf(JlmFormatException.class).hasMessageContaining("embedding");
        assertThatThrownBy(() -> new JlmModel(SMALL, model.embedding(), model.embedding(), model.layers(),
                model.finalGain())).isInstanceOf(JlmFormatException.class).hasMessageContaining("position");
        assertThatThrownBy(() -> new JlmModel(SMALL, model.embedding(), model.position(), model.layers(),
                shortGain)).isInstanceOf(JlmFormatException.class).hasMessageContaining("final gain");
        JlmLayer swapped = new JlmLayer(layer.attentionGain(), layer.query(), layer.key(), layer.value(),
                layer.output(), layer.feedForwardGain(), layer.down(), layer.up());
        assertThatThrownBy(() -> new JlmModel(SMALL, model.embedding(), model.position(),
                List.of(swapped, swapped), model.finalGain())).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("up table must be 12x8");
        assertThatThrownBy(() -> new GainVector(new float[0])).isInstanceOf(JlmFormatException.class);
    }

    @Test
    @DisplayName("Compares gain vectors by content")
    void shouldCompareGainVectorsByContent() {
        // When & Then
        assertThat(new GainVector(new float[] {1f, 2f})).isEqualTo(new GainVector(new float[] {1f, 2f}))
                .hasSameHashCodeAs(new GainVector(new float[] {1f, 2f}))
                .isNotEqualTo(new GainVector(new float[] {1f, 3f})).isNotEqualTo("gain");
    }

    private static byte[] validBytes() {
        return new JlmWriter().toBytes(JlmNetwork.initialize(SMALL, new SplittableRandom(1)).quantize());
    }
}
