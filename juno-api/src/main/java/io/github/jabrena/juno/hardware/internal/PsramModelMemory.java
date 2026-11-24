package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.Psram;

/**
 * A {@link ModelMemory} in a PSRAM buffer. The row loops are native: on the board they are the language model's only
 * C code, and they check every row range against the buffer before touching it. The Java side checks the array
 * ranges, so a native call never reads or writes outside its arrays.
 */
final class PsramModelMemory implements ModelMemory {
    private final int handle;
    private final int size;

    PsramModelMemory(int handle) {
        this.handle = handle;
        this.size = Psram.capacity(handle);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int readByte(int offset) {
        return Psram.readByte(handle, offset);
    }

    @Override
    public void write(int offset, byte[] source, int length) {
        check(Psram.write(handle, offset, source, 0, length));
    }

    @Override
    public void readFloats(int offset, float[] target, int targetOffset, int count) {
        checkRange(target.length, targetOffset, count);
        check(readFloats(handle, offset, target, targetOffset, count));
    }

    @Override
    public void multiplyRows(int offset, int stride, int rows, int skip, int cols, float[] x, int xOffset,
            float[] y) {
        checkRange(x.length, xOffset, cols);
        checkRange(y.length, 0, rows);
        check(multiplyRows(handle, offset, stride, rows, skip, cols, x, xOffset, y));
    }

    @Override
    public void accumulateRows(int offset, int stride, int rows, int skip, int cols, float[] weights, float divisor,
            float[] out, int outOffset) {
        checkRange(weights.length, 0, rows);
        checkRange(out.length, outOffset, cols);
        check(accumulateRows(handle, offset, stride, rows, skip, cols, weights, divisor, out, outOffset));
    }

    @Override
    public void quantizeRow(int offset, float[] x, int cols) {
        checkRange(x.length, 0, cols);
        check(quantizeRow(handle, offset, x, cols));
    }

    @Override
    public void attend(int keyOffset, int valueOffset, int stride, int rows, int skip, int cols, float[] query,
            float[] scores, float divisor, float[] out) {
        checkRange(query.length, skip, cols);
        checkRange(scores.length, 0, rows);
        checkRange(out.length, skip, cols);
        check(attend(handle, keyOffset, valueOffset, stride, rows, skip, cols, query, scores, divisor, out));
    }

    @Override
    public void release() {
        Psram.release(handle);
    }

    private static void checkRange(int capacity, int offset, int length) {
        if (offset < 0 || length < 0 || offset > capacity - length) {
            throw new IllegalArgumentException("language model array range");
        }
    }

    private static void check(int result) {
        if (result < 0) {
            throw new IllegalStateException("language model PSRAM access");
        }
    }

    /** Copies the model linked into the firmware into a new PSRAM buffer; its handle, or a negative error. */
    static native int bundledModel();

    static native int readFloats(int handle, int offset, float[] target, int targetOffset, int count);

    static native int multiplyRows(int handle, int offset, int stride, int rows, int skip, int cols, float[] x,
            int xOffset, float[] y);

    static native int accumulateRows(int handle, int offset, int stride, int rows, int skip, int cols,
            float[] weights, float divisor, float[] out, int outOffset);

    static native int quantizeRow(int handle, int offset, float[] x, int cols);

    static native int attend(int handle, int keyOffset, int valueOffset, int stride, int rows, int skip, int cols,
            float[] query, float[] scores, float divisor, float[] out);
}
