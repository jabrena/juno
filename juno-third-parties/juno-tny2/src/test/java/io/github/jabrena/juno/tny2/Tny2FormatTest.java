package io.github.jabrena.juno.tny2;

import io.github.jabrena.juno.tny2.training.Tny2Network;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TNY2 reader and writer")
class Tny2FormatTest {
    private static final Tny2Config SMALL = new Tny2Config(8, 2, 2, 12);

    @TempDir
    private Path directory;

    @Test
    @DisplayName("Writes the header and reads back an identical model")
    void shouldWriteTheHeaderAndReadBackAnIdenticalModel() throws Exception {
        // Given
        Tny2Model model = Tny2Network.initialize(SMALL, new SplittableRandom(1)).quantize();
        Path file = directory.resolve("models/TINY.BIN");

        // When
        new Tny2Writer().write(model, file);
        Tny2Model read = new Tny2Reader().read(file);

        // Then
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(Arrays.copyOf(bytes, 4)).containsExactly('T', 'N', 'Y', '2');
        assertThat(List.of(header.getShort(4), header.getShort(6), header.getShort(8), header.getShort(10)))
                .containsExactly((short) 8, (short) 2, (short) 2, (short) 12);
        assertThat(bytes).hasSize(SMALL.fileSize());
        assertThat(read).isEqualTo(model);
        assertThat(new Tny2Writer().toBytes(read)).isEqualTo(bytes);
    }

    @Test
    @DisplayName("Writes tables as a little-endian float scale and the row's weights")
    void shouldWriteTablesRowByRow() {
        // Given
        Tny2Model model = Tny2Network.initialize(SMALL, new SplittableRandom(1)).quantize();

        // When
        ByteBuffer bytes = ByteBuffer.wrap(new Tny2Writer().toBytes(model)).order(ByteOrder.LITTLE_ENDIAN);

        // Then
        int secondRow = Tny2Writer.HEADER_BYTES + Float.BYTES + SMALL.width();
        assertThat(bytes.getFloat(Tny2Writer.HEADER_BYTES)).isEqualTo(model.embedding().scale(0));
        assertThat(bytes.get(Tny2Writer.HEADER_BYTES + Float.BYTES)).isEqualTo(model.embedding().weight(0, 0));
        assertThat(bytes.getFloat(secondRow)).isEqualTo(model.embedding().scale(1));
        assertThat(bytes.getFloat(bytes.capacity() - Float.BYTES)).isEqualTo(model.finalGain().get(7));
    }

    @Test
    @DisplayName("Rejects a file without the TNY2 magic")
    void shouldRejectAFileWithoutTheMagic() {
        // Given
        byte[] bytes = validBytes();
        bytes[3] = '1';

        // When & Then
        assertThatThrownBy(() -> new Tny2Reader().read(bytes)).isInstanceOf(Tny2FormatException.class)
                .hasMessageContaining("magic");
    }

    @Test
    @DisplayName("Rejects a truncated file and one with bytes after the model")
    void shouldRejectATruncatedOrOverlongFile() {
        // Given
        byte[] bytes = validBytes();

        // When & Then
        assertThatThrownBy(() -> new Tny2Reader().read(Arrays.copyOf(bytes, bytes.length - 1)))
                .isInstanceOf(Tny2FormatException.class).hasMessageContaining("truncated");
        assertThatThrownBy(() -> new Tny2Reader().read(Arrays.copyOf(bytes, 2)))
                .isInstanceOf(Tny2FormatException.class).hasMessageContaining("truncated");
        assertThatThrownBy(() -> new Tny2Reader().read(Arrays.copyOf(bytes, bytes.length + 3)))
                .isInstanceOf(Tny2FormatException.class).hasMessageContaining("3 bytes after the model");
    }

    @Test
    @DisplayName("Rejects dimensions the board engine refuses")
    void shouldRejectDimensionsTheBoardEngineRefuses() {
        // Given
        byte[] bytes = validBytes();
        bytes[6] = 9;

        // When & Then
        assertThatThrownBy(() -> new Tny2Reader().read(bytes)).isInstanceOf(Tny2FormatException.class)
                .hasMessageContaining("layers");
    }

    @Test
    @DisplayName("Rejects a model whose tables do not match its header")
    void shouldRejectAModelWhoseTablesDoNotMatchItsHeader() {
        // Given
        Tny2Model model = Tny2Network.initialize(SMALL, new SplittableRandom(1)).quantize();
        Tny2Layer layer = model.layers().getFirst();
        GainVector shortGain = new GainVector(new float[] {1f});

        // When & Then
        assertThatThrownBy(() -> new Tny2Model(SMALL, model.embedding(), model.position(), List.of(layer),
                model.finalGain())).isInstanceOf(Tny2FormatException.class).hasMessageContaining("2 layers");
        assertThatThrownBy(() -> new Tny2Model(SMALL, model.position(), model.position(), model.layers(),
                model.finalGain())).isInstanceOf(Tny2FormatException.class).hasMessageContaining("embedding");
        assertThatThrownBy(() -> new Tny2Model(SMALL, model.embedding(), model.embedding(), model.layers(),
                model.finalGain())).isInstanceOf(Tny2FormatException.class).hasMessageContaining("position");
        assertThatThrownBy(() -> new Tny2Model(SMALL, model.embedding(), model.position(), model.layers(),
                shortGain)).isInstanceOf(Tny2FormatException.class).hasMessageContaining("final gain");
        Tny2Layer swapped = new Tny2Layer(layer.attentionGain(), layer.query(), layer.key(), layer.value(),
                layer.output(), layer.feedForwardGain(), layer.down(), layer.up());
        assertThatThrownBy(() -> new Tny2Model(SMALL, model.embedding(), model.position(),
                List.of(swapped, swapped), model.finalGain())).isInstanceOf(Tny2FormatException.class)
                .hasMessageContaining("up table must be 12x8");
        assertThatThrownBy(() -> new GainVector(new float[0])).isInstanceOf(Tny2FormatException.class);
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
        return new Tny2Writer().toBytes(Tny2Network.initialize(SMALL, new SplittableRandom(1)).quantize());
    }
}
