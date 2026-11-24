package io.github.jabrena.juno.hardware.internal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** A {@link ModelMemory} over a heap array, with the row kernels of {@code language-model.cpp} in the same order. */
final class ArrayModelMemory implements ModelMemory {
    private final ByteBuffer bytes;
    private boolean released;

    ArrayModelMemory(int size) {
        bytes = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    ArrayModelMemory(byte[] content) {
        this(content.length);
        bytes.put(0, content);
    }

    boolean released() {
        return released;
    }

    @Override
    public int size() {
        return bytes.capacity();
    }

    @Override
    public int readByte(int offset) {
        return bytes.get(offset) & 0xFF;
    }

    @Override
    public void write(int offset, byte[] source, int length) {
        bytes.put(offset, source, 0, length);
    }

    @Override
    public void readFloats(int offset, float[] target, int targetOffset, int count) {
        for (int i = 0; i < count; i++) {
            target[targetOffset + i] = bytes.getFloat(offset + 4 * i);
        }
    }

    @Override
    public void multiplyRows(int offset, int stride, int rows, int skip, int cols, float[] x, int xOffset,
            float[] y) {
        for (int r = 0; r < rows; r++) {
            int row = offset + r * stride;
            float sum = 0f;
            for (int c = 0; c < cols; c++) {
                sum += bytes.get(row + 4 + skip + c) * x[xOffset + c];
            }
            y[r] = sum * bytes.getFloat(row);
        }
    }

    @Override
    public void accumulateRows(int offset, int stride, int rows, int skip, int cols, float[] weights, float divisor,
            float[] out, int outOffset) {
        for (int r = 0; r < rows; r++) {
            if (weights[r] == 0f) {
                continue;
            }
            int row = offset + r * stride;
            float scale = weights[r] * bytes.getFloat(row) / divisor;
            for (int c = 0; c < cols; c++) {
                out[outOffset + c] += scale * bytes.get(row + 4 + skip + c);
            }
        }
    }

    @Override
    public void quantizeRow(int offset, float[] x, int cols) {
        float largest = 0f;
        for (int i = 0; i < cols; i++) {
            largest = Math.max(largest, Math.abs(x[i]));
        }
        largest = Math.max(largest, 1e-20f);
        float factor = 127f / largest;
        bytes.putFloat(offset, largest / 127f);
        for (int i = 0; i < cols; i++) {
            float value = x[i] * factor;
            int level = (int) (value < 0 ? value - 0.5f : value + 0.5f);
            bytes.put(offset + 4 + i, (byte) Math.clamp(level, -127, 127));
        }
    }

    @Override
    public void attend(int keyOffset, int valueOffset, int stride, int rows, int skip, int cols, float[] query,
            float[] scores, float divisor, float[] out) {
        multiplyRows(keyOffset, stride, rows, skip, cols, query, skip, scores);
        float largest = -1e30f;
        for (int j = 0; j < rows; j++) {
            scores[j] = scores[j] / divisor;
            largest = Math.max(largest, scores[j]);
        }
        float sum = 0f;
        for (int j = 0; j < rows; j++) {
            scores[j] = scores[j] < -60f ? 0f : (float) Math.exp(scores[j] - largest);
            sum += scores[j];
        }
        for (int i = 0; i < cols; i++) {
            out[skip + i] = 0f;
        }
        accumulateRows(valueOffset, stride, rows, skip, cols, scores, sum, out, skip);
    }

    @Override
    public void release() {
        released = true;
    }
}
