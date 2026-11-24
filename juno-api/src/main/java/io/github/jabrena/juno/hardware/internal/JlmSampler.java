package io.github.jabrena.juno.hardware.internal;

/**
 * Picks each next token from the model's scores, as {@code JlmInference} does: the likeliest one at creativity 0, or
 * a sample among the likely ones with temperature {@code creativity / 5}. It also reports how certain the likeliest
 * token was, which decides whether the engine answers or says it does not know. Its buffer is allocated once.
 */
final class JlmSampler {
    private static final float CREATIVITY_STEP = 5f;
    private static final float SAMPLE_FLOOR = 0.15f;
    private static final float EXP_LIMIT = -60f;

    private final JlmRuntime runtime;
    private final float[] weights = new float[JlmLayout.MAX_VOCAB];
    private float confidence;

    JlmSampler(JlmRuntime runtime) {
        this.runtime = runtime;
    }

    /** The next token among {@code scores[0..vocabulary)}. */
    int pick(float[] scores, int vocabulary, int creativity) {
        int best = 0;
        for (int i = 1; i < vocabulary; i++) {
            if (scores[i] > scores[best]) {
                best = i;
            }
        }
        // scores[i] - scores[best] is never above 0; below EXP_LIMIT its exponential (under 1e-26) cannot change a
        // sum of at least 1, so the runtime's cutoff to 0 gives the same sum as clamping.
        for (int i = 0; i < vocabulary; i++) {
            weights[i] = scores[i] - scores[best];
        }
        runtime.exponentials(weights, vocabulary, 0f, EXP_LIMIT);
        float sum = 0f;
        for (int i = 0; i < vocabulary; i++) {
            sum += weights[i];
        }
        confidence = 1f / sum;
        return creativity == 0 ? best : sample(scores, vocabulary, best, creativity);
    }

    /** The probability of the likeliest token at the last {@link #pick}. */
    float confidence() {
        return confidence;
    }

    /** Samples among the likely tokens, ignoring weights under the floor. */
    private int sample(float[] scores, int vocabulary, int best, int creativity) {
        float temperature = creativity / CREATIVITY_STEP;
        for (int i = 0; i < vocabulary; i++) {
            weights[i] = (scores[i] - scores[best]) / temperature;
        }
        runtime.exponentials(weights, vocabulary, 0f, EXP_LIMIT);
        float total = 0f;
        for (int i = 0; i < vocabulary; i++) {
            weights[i] = weights[i] < SAMPLE_FLOOR ? 0f : weights[i];
            total += weights[i];
        }
        float remaining = runtime.nextFloat() * total;
        for (int i = 0; i < vocabulary; i++) {
            remaining -= weights[i];
            if (remaining < 0 && weights[i] > 0) {
                return i;
            }
        }
        return best;
    }
}
