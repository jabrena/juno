package io.github.jabrena.juno.dm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JevModelCodecTest {
    @Test
    void roundTripsAQuantizedJev1Model() {
        var model = QuantizedJevModel.from(new JevModel(
                new float[][] {
                    {0.25f, 0.5f},
                    {0.5f, 0.75f}
                },
                new float[] {0.1f, -0.2f}));
        var codec = new JevModelCodec();

        QuantizedJevModel restored = codec.read(codec.write(model));

        var inference = new QuantizedJevInference(restored);
        assertThat(inference.evaluate(new float[] {1f, 1f}, new int[] {0, 1}, 2)).isEqualTo(1);
    }

    @Test
    void rejectsMalformedJev1Data() {
        var codec = new JevModelCodec();

        assertThatThrownBy(() -> codec.read(new byte[] {'J', 'E', 'V', '1', 1, 0, 1, 0, 1}))
                .isInstanceOf(JevModelException.class);
    }
}
