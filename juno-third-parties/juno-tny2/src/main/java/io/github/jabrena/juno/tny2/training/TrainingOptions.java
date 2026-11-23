package io.github.jabrena.juno.tny2.training;

/**
 * How long and how fast to train.
 *
 * @param steps optimizer steps
 * @param batchSize examples per step
 * @param learningRate peak Adam step size, reached after {@code warmupSteps}
 * @param finalLearningRate fraction of the peak left at the last step, after a cosine decay
 * @param warmupSteps steps of linear warm-up from zero
 * @param clipNorm largest global gradient norm; larger gradients are scaled down to it
 * @param threads worker threads; the result is reproducible for the same seed and thread count
 * @param logEvery steps between progress lines; 0 never logs
 */
public record TrainingOptions(int steps, int batchSize, float learningRate, float finalLearningRate, int warmupSteps,
        float clipNorm, int threads, int logEvery) {

    public TrainingOptions {
        positive("steps", steps);
        positive("batchSize", batchSize);
        positive("threads", threads);
        if (!(learningRate > 0)) {
            throw new IllegalArgumentException("learningRate must be positive, not " + learningRate);
        }
        if (!(finalLearningRate >= 0 && finalLearningRate <= 1)) {
            throw new IllegalArgumentException("finalLearningRate must be 0 to 1, not " + finalLearningRate);
        }
        if (warmupSteps < 0 || logEvery < 0) {
            throw new IllegalArgumentException("warmupSteps and logEvery must not be negative");
        }
        if (!(clipNorm > 0)) {
            throw new IllegalArgumentException("clipNorm must be positive, not " + clipNorm);
        }
    }

    /** Defaults for {@code steps} steps on every core. */
    public static TrainingOptions defaults(int steps) {
        return new TrainingOptions(steps, 32, 3e-3f, 0.1f, Math.min(100, steps / 10), 1f,
                Runtime.getRuntime().availableProcessors(), 100);
    }

    /** The step size at {@code step}, counted from 0: linear warm-up, then cosine decay to the final fraction. */
    float learningRateAt(int step) {
        if (step < warmupSteps) {
            return learningRate * (step + 1) / warmupSteps;
        }
        int decaySteps = Math.max(1, steps - warmupSteps - 1);
        double progress = Math.min(1.0, (double) (step - warmupSteps) / decaySteps);
        double cosine = 0.5 * (1 + Math.cos(Math.PI * progress));
        return (float) (learningRate * (finalLearningRate + (1 - finalLearningRate) * cosine));
    }

    private static void positive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException("%s must be positive, not %d".formatted(name, value));
        }
    }
}
