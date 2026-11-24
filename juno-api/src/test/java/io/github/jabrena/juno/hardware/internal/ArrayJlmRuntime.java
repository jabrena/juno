package io.github.jabrena.juno.hardware.internal;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** A {@link JlmRuntime} on the heap: a fixed bundled model, an optional allocation limit and seeded sampling. */
final class ArrayJlmRuntime implements JlmRuntime {
    private final byte @Nullable [] bundled;
    private final int limit;
    private final SplittableRandom random;
    final List<ArrayModelMemory> blocks = new ArrayList<>();

    ArrayJlmRuntime(byte @Nullable [] bundled, int limit, long seed) {
        this.bundled = bundled;
        this.limit = limit;
        this.random = new SplittableRandom(seed);
    }

    @Override
    public @Nullable ModelMemory bundledModel() {
        return bundled == null ? null : track(new ArrayModelMemory(bundled));
    }

    @Override
    public @Nullable ModelMemory allocate(int bytes) {
        return bytes > limit ? null : track(new ArrayModelMemory(bytes));
    }

    @Override
    public float nextFloat() {
        return random.nextFloat();
    }

    @Override
    public void exponentials(float[] values, int count, float shift, float cutoff) {
        for (int i = 0; i < count; i++) {
            values[i] = values[i] < cutoff ? 0f : (float) Math.exp(values[i] - shift);
        }
    }

    @Override
    public void rmsNorm(float[] x, float[] gains, int gainOffset, float[] y, int n) {
        float sum = 0f;
        for (int i = 0; i < n; i++) {
            sum += x[i] * x[i];
        }
        float scale = (float) (1.0 / Math.sqrt(sum / n + 1e-5f));
        for (int i = 0; i < n; i++) {
            y[i] = x[i] * scale * gains[gainOffset + i];
        }
    }

    @Override
    public void add(float[] target, float[] source, int n) {
        for (int i = 0; i < n; i++) {
            target[i] += source[i];
        }
    }

    @Override
    public void relu(float[] x, int n) {
        for (int i = 0; i < n; i++) {
            x[i] = Math.max(x[i], 0f);
        }
    }

    private ModelMemory track(ArrayModelMemory memory) {
        blocks.add(memory);
        return memory;
    }
}
