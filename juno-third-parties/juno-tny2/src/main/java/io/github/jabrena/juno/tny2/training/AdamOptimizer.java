package io.github.jabrena.juno.tny2.training;

/**
 * Adam with bias correction over one flat parameter array, with optional decoupled weight decay (AdamW) on the
 * values its mask selects.
 */
final class AdamOptimizer {
    private static final float BETA1 = 0.9f;
    private static final float BETA2 = 0.99f;
    private static final float EPSILON = 1e-8f;

    private final float[] firstMoment;
    private final float[] secondMoment;
    private final float weightDecay;
    private final boolean[] decayed;
    private int steps;

    AdamOptimizer(int size) {
        this(size, 0f, new boolean[size]);
    }

    /** {@code decayed[i]} says whether value {@code i} decays by {@code weightDecay} times the step size each step. */
    AdamOptimizer(int size, float weightDecay, boolean[] decayed) {
        firstMoment = new float[size];
        secondMoment = new float[size];
        this.weightDecay = weightDecay;
        this.decayed = decayed.clone();
    }

    /** Moves {@code weights} against {@code gradient} with step size {@code learningRate}. */
    void update(float[] weights, float[] gradient, float learningRate) {
        steps++;
        float firstCorrection = (float) (1 - Math.pow(BETA1, steps));
        float secondCorrection = (float) (1 - Math.pow(BETA2, steps));
        for (int i = 0; i < weights.length; i++) {
            float g = gradient[i];
            firstMoment[i] = BETA1 * firstMoment[i] + (1 - BETA1) * g;
            secondMoment[i] = BETA2 * secondMoment[i] + (1 - BETA2) * g * g;
            float m = firstMoment[i] / firstCorrection;
            float v = secondMoment[i] / secondCorrection;
            float decay = decayed[i] ? weightDecay * weights[i] : 0f;
            weights[i] -= learningRate * (m / ((float) Math.sqrt(v) + EPSILON) + decay);
        }
    }
}
