package io.github.jabrena.juno.tny2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Quantized table")
class QuantizedTableTest {

    @Test
    @DisplayName("Maps each row's largest magnitude to 127 and rounds half away from zero")
    void shouldQuantizeEachRowSymmetrically() {
        // Given
        float[] values = {9, 0, 0, 2, 3, 1.5f, -6, 0, 0, 0, 0, 0};

        // When
        QuantizedTable table = QuantizedTable.quantize(3, 4, values, 0);

        // Then
        assertThat(table.scale(0)).isEqualTo(9f / 127f);
        assertThat(table.weight(0, 0)).isEqualTo((byte) 127);
        assertThat(table.weight(0, 3)).isEqualTo((byte) 28);
        assertThat(table.weight(1, 0)).isEqualTo((byte) 64);
        assertThat(table.weight(1, 1)).isEqualTo((byte) 32);
        assertThat(table.weight(1, 2)).isEqualTo((byte) -127);
        assertThat(table.scale(2)).isZero();
        assertThat(table.weight(2, 0)).isZero();
        assertThat(table.value(0, 3)).isCloseTo(2f, within(9f / 254f));
    }

    @Test
    @DisplayName("Quantizes a slice of a larger array from its offset")
    void shouldQuantizeFromAnOffset() {
        // When
        QuantizedTable table = QuantizedTable.quantize(1, 2, new float[] {100, 1, -2}, 1);

        // Then
        assertThat(table.weight(0, 0)).isEqualTo((byte) 64);
        assertThat(table.weight(0, 1)).isEqualTo((byte) -127);
    }

    @Test
    @DisplayName("Rounds levels half away from zero and clamps them to plus or minus 127")
    void shouldRoundAndClampLevels() {
        // When & Then
        assertThat(QuantizedTable.level(0.5f)).isEqualTo((byte) 1);
        assertThat(QuantizedTable.level(-0.5f)).isEqualTo((byte) -1);
        assertThat(QuantizedTable.level(0.49f)).isZero();
        assertThat(QuantizedTable.level(130f)).isEqualTo((byte) 127);
        assertThat(QuantizedTable.level(-130f)).isEqualTo((byte) -127);
    }

    @Test
    @DisplayName("Multiplies a vector and dequantizes like the board engine")
    void shouldMultiplyAndDequantize() {
        // Given
        QuantizedTable table = new QuantizedTable(2, 2, new float[] {0.5f, 2f}, new byte[] {1, 2, -3, 4});
        float[] product = new float[2];
        float[] values = new float[5];

        // When
        table.multiply(new float[] {10, 1}, product);
        table.dequantize(values, 1);

        // Then
        assertThat(product).containsExactly(6f, -52f);
        assertThat(values).containsExactly(0f, 0.5f, 1f, -6f, 8f);
        assertThat(table.rows()).isEqualTo(2);
        assertThat(table.cols()).isEqualTo(2);
    }

    @Test
    @DisplayName("Copies its arrays and compares by content")
    void shouldCopyItsArraysAndCompareByContent() {
        // Given
        float[] scales = {1f};
        byte[] weights = {5};
        QuantizedTable table = new QuantizedTable(1, 1, scales, weights);

        // When
        scales[0] = 2f;
        weights[0] = 6;

        // Then
        assertThat(table.value(0, 0)).isEqualTo(5f);
        assertThat(table).isEqualTo(new QuantizedTable(1, 1, new float[] {1f}, new byte[] {5}))
                .hasSameHashCodeAs(new QuantizedTable(1, 1, new float[] {1f}, new byte[] {5}))
                .isNotEqualTo(new QuantizedTable(1, 1, new float[] {1f}, new byte[] {4}))
                .isNotEqualTo(new QuantizedTable(1, 1, new float[] {2f}, new byte[] {5}))
                .isNotEqualTo("table");
    }

    @Test
    @DisplayName("Rejects mismatched shapes")
    void shouldRejectMismatchedShapes() {
        // When & Then
        assertThatThrownBy(() -> new QuantizedTable(0, 1, new float[0], new byte[0]))
                .isInstanceOf(Tny2FormatException.class).hasMessageContaining("0x1");
        assertThatThrownBy(() -> new QuantizedTable(1, 0, new float[1], new byte[0]))
                .isInstanceOf(Tny2FormatException.class);
        assertThatThrownBy(() -> new QuantizedTable(2, 2, new float[1], new byte[4]))
                .isInstanceOf(Tny2FormatException.class).hasMessageContaining("1 scales");
        assertThatThrownBy(() -> new QuantizedTable(2, 2, new float[2], new byte[3]))
                .isInstanceOf(Tny2FormatException.class).hasMessageContaining("3 weights");
    }
}
