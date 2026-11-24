package io.github.jabrena.juno.hardware.internal;

/**
 * A block of a language model's memory: the JLM1 file as it was loaded, or the attention cache. The engine reads it
 * through int8 rows, each a little-endian {@code float} scale followed by its signed-byte values, which is how a JLM1
 * table stores a row and how the cache stores a key or a value. The row loops run here, next to the memory, so on the
 * board they are the only native code of the engine.
 */
public interface ModelMemory {

    /** Bytes in the block. */
    int size();

    /** The unsigned byte at {@code offset}. */
    int readByte(int offset);

    /** Copies {@code length} bytes of {@code source} to {@code offset}. */
    void write(int offset, byte[] source, int length);

    /** Reads {@code count} little-endian floats from {@code offset} into {@code target} from {@code targetOffset}. */
    void readFloats(int offset, float[] target, int targetOffset, int count);

    /**
     * For each of {@code rows} rows, {@code stride} bytes apart from {@code offset}, sets {@code y[r]} to the row's
     * scale times the sum over {@code c < cols} of value {@code skip + c} times {@code x[xOffset + c]}, summed in
     * column order.
     */
    void multiplyRows(int offset, int stride, int rows, int skip, int cols, float[] x, int xOffset, float[] y);

    /**
     * Adds to {@code out[outOffset + c]}, for each row {@code r} whose weight is not zero, value {@code skip + c} of
     * the row times {@code weights[r] * scale / divisor}, row by row.
     */
    void accumulateRows(int offset, int stride, int rows, int skip, int cols, float[] weights, float divisor,
            float[] out, int outOffset);

    /**
     * Writes {@code x[0..cols)} at {@code offset} as one row: the scale is the largest magnitude over 127 (at least
     * {@code 1e-20 / 127}), and each value rounds half away from zero.
     */
    void quantizeRow(int offset, float[] x, int cols);

    /**
     * One attention head over the cache: {@code rows} keys from {@code keyOffset} and values from {@code valueOffset},
     * {@code stride} bytes apart, each using its values {@code skip} to {@code skip + cols}. Scores each key as the
     * dot product with {@code query[skip..skip + cols)} times its scale over {@code divisor} into {@code scores}, turns
     * them into softmax weights (0 below -60), and writes the weighted sum of the values into
     * {@code out[skip..skip + cols)}.
     */
    void attend(int keyOffset, int valueOffset, int stride, int rows, int skip, int cols, float[] query,
            float[] scores, float divisor, float[] out);

    /** Frees the block; the object is unusable afterwards. */
    void release();
}
