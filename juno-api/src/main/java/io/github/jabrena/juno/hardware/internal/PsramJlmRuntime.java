package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.Psram;
import io.github.jabrena.juno.util.Random;
import org.jspecify.annotations.Nullable;

/** The board's {@link JlmRuntime}: models and caches in PSRAM, and the board's random generator. */
final class PsramJlmRuntime implements JlmRuntime {
    private static final int SAMPLE_BITS = 1 << 24;

    @Override
    public @Nullable ModelMemory bundledModel() {
        int handle = PsramModelMemory.bundledModel();
        return handle < 0 ? null : new PsramModelMemory(handle);
    }

    @Override
    public @Nullable ModelMemory allocate(int bytes) {
        int handle = Psram.allocate(bytes);
        return handle < 0 ? null : new PsramModelMemory(handle);
    }

    @Override
    public float nextFloat() {
        return Random.nextInt(SAMPLE_BITS) / (float) SAMPLE_BITS;
    }

    @Override
    public void exponentials(float[] values, int count, float shift, float cutoff) {
        checkRange(values.length, 0, count);
        singleExponentials(values, count, shift, cutoff);
    }

    @Override
    public void rmsNorm(float[] x, float[] gains, int gainOffset, float[] y, int n) {
        checkRange(x.length, 0, n);
        checkRange(y.length, 0, n);
        checkRange(gains.length, gainOffset, n);
        rmsNormKernel(x, gains, gainOffset, y, n);
    }

    @Override
    public void add(float[] target, float[] source, int n) {
        checkRange(target.length, 0, n);
        checkRange(source.length, 0, n);
        addKernel(target, source, n);
    }

    @Override
    public void relu(float[] x, int n) {
        checkRange(x.length, 0, n);
        reluKernel(x, n);
    }

    private static void checkRange(int capacity, int offset, int length) {
        if (offset < 0 || length < 0 || offset > capacity - length) {
            throw new IllegalArgumentException("language model array range");
        }
    }

    static native int rmsNormKernel(float[] x, float[] gains, int gainOffset, float[] y, int n);

    static native int addKernel(float[] target, float[] source, int n);

    static native int reluKernel(float[] x, int n);

    /** The single-precision {@code expf} loop of {@link #exponentials}. */
    static native int singleExponentials(float[] values, int count, float shift, float cutoff);
}
