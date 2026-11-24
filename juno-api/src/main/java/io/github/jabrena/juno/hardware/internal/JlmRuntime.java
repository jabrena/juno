package io.github.jabrena.juno.hardware.internal;

import org.jspecify.annotations.Nullable;

/** What the language-model engine needs from the board: memory for a model and its cache, and random numbers. */
public interface JlmRuntime {

    /** A fresh copy of the model the compiler linked into the firmware, or {@code null} when it does not fit. */
    @Nullable ModelMemory bundledModel();

    /** A zeroed block of {@code bytes}, or {@code null} when it does not fit. */
    @Nullable ModelMemory allocate(int bytes);

    /** A uniformly distributed float in {@code [0, 1)}, for sampling. */
    float nextFloat();

    /**
     * Sets each of the first {@code count} values to 0 when it is below {@code cutoff}, and otherwise to
     * {@code e^(value - shift)} in single precision: the softmax exponentials, which in Java's double-precision
     * {@code Math.exp} would dominate the engine's time on a board without a double-precision unit.
     */
    void exponentials(float[] values, int count, float shift, float cutoff);

    /**
     * RMS-normalizes {@code x[0..n)} into {@code y}: each value times {@code 1 / sqrt(mean of squares + 1e-5)} times
     * {@code gains[gainOffset + i]}.
     */
    void rmsNorm(float[] x, float[] gains, int gainOffset, float[] y, int n);

    /** Adds {@code source[0..n)} to {@code target}, element by element. */
    void add(float[] target, float[] source, int n);

    /** Sets every negative value of {@code x[0..n)} to 0. */
    void relu(float[] x, int n);
}
