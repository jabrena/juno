package io.github.jabrena.juno.lm;

import io.github.jabrena.juno.lm.training.JlmNetwork;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TNY2 import")
class Tny2ImportTest {
    private static final JlmConfig SMALL = new JlmConfig(8, 2, 2, 12);

    @Test
    @DisplayName("Reads a TNY2 file as the same model over the ASCII vocabulary and a 128-position context")
    void shouldReadATny2FileAsTheSameModel() {
        // Given
        JlmModel model = JlmNetwork.initialize(SMALL, new SplittableRandom(1)).quantize();

        // When
        JlmModel imported = new Tny2Import().read(tny2(model));

        // Then
        assertThat(imported).isEqualTo(model);
        assertThat(imported.config().vocabulary()).isEqualTo(JlmVocabulary.ASCII);
        assertThat(imported.config().context()).isEqualTo(128);
    }

    @Test
    @DisplayName("Rejects a file that is not TNY2 or is truncated")
    void shouldRejectAFileThatIsNotTny2() {
        // Given
        JlmModel model = JlmNetwork.initialize(SMALL, new SplittableRandom(1)).quantize();
        byte[] tny2 = tny2(model);

        // When & Then
        assertThatThrownBy(() -> new Tny2Import().read(new JlmWriter().toBytes(model)))
                .isInstanceOf(JlmFormatException.class).hasMessageContaining("not a TNY2 file");
        assertThatThrownBy(() -> new Tny2Import().read(Arrays.copyOf(tny2, tny2.length - 1)))
                .isInstanceOf(JlmFormatException.class).hasMessageContaining("TNY2 file is truncated");
    }

    /** The TNY2 layout: its 12-byte header, then the same tables as JLM1. */
    private static byte[] tny2(JlmModel model) {
        byte[] jlm = new JlmWriter().toBytes(model);
        int body = jlm.length - model.config().headerSize();
        ByteBuffer buffer = ByteBuffer.allocate(12 + body).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[] {'T', 'N', 'Y', '2'}).putShort((short) 8).putShort((short) 2).putShort((short) 2)
                .putShort((short) 12).put(jlm, model.config().headerSize(), body);
        return buffer.array();
    }
}
