package io.github.jabrena.juno.tny2;

import java.util.Arrays;

/**
 * A row-major matrix stored as signed bytes with one {@code float} scale per row: element {@code (r, c)} is
 * {@code weight(r, c) * scale(r)}. Rows are outputs and columns inputs, so a matrix-vector product reads one row per
 * output value.
 */
public final class QuantizedTable {
    private static final float LEVELS = 127f;

    private final int rows;
    private final int cols;
    private final float[] scales;
    private final byte[] weights;

    public QuantizedTable(int rows, int cols, float[] scales, byte[] weights) {
        if (rows < 1 || cols < 1) {
            throw new Tny2FormatException("table must have rows and columns, not %dx%d".formatted(rows, cols));
        }
        if (scales.length != rows || weights.length != rows * cols) {
            throw new Tny2FormatException("table %dx%d has %d scales and %d weights".formatted(rows, cols,
                    scales.length, weights.length));
        }
        this.rows = rows;
        this.cols = cols;
        this.scales = scales.clone();
        this.weights = weights.clone();
    }

    /**
     * Quantizes {@code rows * cols} row-major values symmetrically per row: the largest magnitude maps to 127 and
     * every value rounds half away from zero.
     */
    public static QuantizedTable quantize(int rows, int cols, float[] values, int offset) {
        float[] scales = new float[rows];
        byte[] weights = new byte[rows * cols];
        for (int r = 0; r < rows; r++) {
            int start = offset + r * cols;
            float largest = 0f;
            for (int c = 0; c < cols; c++) {
                largest = Math.max(largest, Math.abs(values[start + c]));
            }
            scales[r] = largest / LEVELS;
            for (int c = 0; c < cols; c++) {
                weights[r * cols + c] = largest == 0f ? 0 : level(values[start + c] * LEVELS / largest);
            }
        }
        return new QuantizedTable(rows, cols, scales, weights);
    }

    /** Rounds half away from zero and clamps to the symmetric signed-byte range, as the board engine does. */
    static byte level(float value) {
        int rounded = (int) (value < 0 ? value - 0.5f : value + 0.5f);
        return (byte) Math.clamp(rounded, -127, 127);
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    public float scale(int row) {
        return scales[row];
    }

    public byte weight(int row, int col) {
        return weights[row * cols + col];
    }

    public float value(int row, int col) {
        return weight(row, col) * scales[row];
    }

    /** {@code y[r] = scale(r) * sum_c weight(r, c) * x[c]}, the product the board engine computes. */
    void multiply(float[] x, float[] y) {
        int index = 0;
        for (int r = 0; r < rows; r++) {
            float sum = 0f;
            for (int c = 0; c < cols; c++) {
                sum += weights[index++] * x[c];
            }
            y[r] = sum * scales[r];
        }
    }

    /** Writes the dequantized values row-major into {@code target} from {@code offset}. */
    public void dequantize(float[] target, int offset) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                target[offset + r * cols + c] = value(r, c);
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuantizedTable table && rows == table.rows && cols == table.cols
                && Arrays.equals(scales, table.scales) && Arrays.equals(weights, table.weights);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(scales) + Arrays.hashCode(weights);
    }
}
