package io.github.jabrena.juno.lm.training;

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
 * @param weightDecay decoupled (AdamW) weight decay of the matrices, which discourages memorizing; gains never decay
 * @param dropout fraction of each residual branch's outputs dropped in training, from 0 up to but excluding 1
 * @param evalEvery steps between checkpoints, where {@link JlmTrainer} hands the network to its checkpoint; 0 only
 *     at the end
 */
public record TrainingOptions(int steps, int batchSize, float learningRate, float finalLearningRate, int warmupSteps,
        float clipNorm, int threads, int logEvery, float weightDecay, float dropout, int evalEvery) {

    /** Options without regularization or intermediate checkpoints. */
    public TrainingOptions(int steps, int batchSize, float learningRate, float finalLearningRate, int warmupSteps,
            float clipNorm, int threads, int logEvery) {
        this(steps, batchSize, learningRate, finalLearningRate, warmupSteps, clipNorm, threads, logEvery, 0f, 0f, 0);
    }

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
        if (!(weightDecay >= 0 && weightDecay < 1)) {
            throw new IllegalArgumentException("weightDecay must be 0 up to 1, not " + weightDecay);
        }
        if (!(dropout >= 0 && dropout < 1)) {
            throw new IllegalArgumentException("dropout must be 0 up to 1, not " + dropout);
        }
        if (evalEvery < 0) {
            throw new IllegalArgumentException("evalEvery must not be negative, not " + evalEvery);
        }
    }

    /** Defaults for {@code steps} steps on every core, with light regularization and a checkpoint every 250 steps. */
    public static TrainingOptions defaults(int steps) {
        return new TrainingOptions(steps, 32, 3e-3f, 0.1f, Math.min(100, steps / 10), 1f,
                Runtime.getRuntime().availableProcessors(), 100, 0.01f, 0.1f, 250);
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
