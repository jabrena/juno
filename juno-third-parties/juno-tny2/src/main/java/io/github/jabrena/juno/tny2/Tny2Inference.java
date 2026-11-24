package io.github.jabrena.juno.tny2;

import java.util.random.RandomGenerator;

/**
 * Runs a quantized model exactly as the board's TINY engine does, so its answers are the reference for the board:
 * the question is fed one character per position, then {@link Tny2Vocabulary#END}, then characters are picked until
 * the model picks {@code END} or the context is full. If any of the first {@link #LOOKAHEAD} picks is less certain
 * than the floor, the answer is {@link #UNKNOWN} instead of a guess. Keys and values are cached as signed bytes with
 * one scale per position, as on the board. One instance holds the attention cache, so it is not thread-safe.
 */
public final class Tny2Inference {
    /** Highest creativity; 0 always picks the most likely character. */
    public static final int MAX_CREATIVITY = 9;
    private static final float CREATIVITY_STEP = 5f;
    private static final float SAMPLE_FLOOR = 0.15f;
    private static final float EXP_LIMIT = -60f;
    private static final float NORM_EPSILON = 1e-5f;
    private static final float CACHE_FLOOR = 1e-20f;
    /** Answer characters, counting a closing END, whose certainty decides between the answer and {@link #UNKNOWN}. */
    public static final int LOOKAHEAD = 8;
    /** Below this certainty, the probability of the most likely character, a lookahead pick gives up the answer. */
    public static final float CONFIDENCE_FLOOR = 0.5f;
    /** What an uncertain model answers instead of guessing. */
    public static final String UNKNOWN = "I do not know.";

    private final Tny2Model model;
    private final Tny2Vocabulary vocabulary = Tny2Vocabulary.ASCII;
    private final int width;
    private final int heads;
    private final int headWidth;
    private final float attentionDivisor;
    private final byte[][] keyCache;
    private final byte[][] valueCache;
    private final float[][] keyScales;
    private final float[][] valueScales;
    private final float[] state;
    private final float[] normalized;
    private final float[] query;
    private final float[] temporary;
    private final float[] attended;
    private final float[] hidden;
    private final float[] weights;
    private final float[] scores = new float[Tny2Vocabulary.SIZE];
    private final float confidenceFloor;
    private float confidence;

    public Tny2Inference(Tny2Model model) {
        this(model, CONFIDENCE_FLOOR);
    }

    /**
     * An engine with its own floor, to tune {@link #CONFIDENCE_FLOOR}: 0 never falls back. The board uses
     * {@link #CONFIDENCE_FLOOR}.
     */
    public Tny2Inference(Tny2Model model, float confidenceFloor) {
        this.confidenceFloor = confidenceFloor;
        this.model = model;
        Tny2Config config = model.config();
        width = config.width();
        heads = config.heads();
        headWidth = config.headWidth();
        attentionDivisor = (float) Math.sqrt(headWidth);
        keyCache = new byte[config.layers()][Tny2Config.CONTEXT * width];
        valueCache = new byte[config.layers()][Tny2Config.CONTEXT * width];
        keyScales = new float[config.layers()][Tny2Config.CONTEXT];
        valueScales = new float[config.layers()][Tny2Config.CONTEXT];
        state = new float[width];
        normalized = new float[width];
        query = new float[width];
        temporary = new float[width];
        attended = new float[width];
        hidden = new float[config.hidden()];
        weights = new float[Math.max(Tny2Config.CONTEXT, Tny2Vocabulary.SIZE)];
    }

    /** The greedy answer: the most likely character at every position. */
    public String answer(String question) {
        return answer(question, 0, RandomGenerator.getDefault());
    }

    /**
     * Answers a question. Above 0, {@code creativity} samples among the likely characters with temperature
     * {@code creativity / 5}.
     */
    public String answer(String question, int creativity, RandomGenerator random) {
        if (creativity < 0 || creativity > MAX_CREATIVITY) {
            throw new IllegalArgumentException("creativity must be 0 to 9, not " + creativity);
        }
        String prompt = vocabulary.question(question);
        if (prompt.isEmpty()) {
            throw new Tny2FormatException("question is empty");
        }
        int position = 0;
        for (; position < prompt.length() && position < Tny2Config.CONTEXT - 1; position++) {
            step(vocabulary.token(prompt.charAt(position)), position);
        }
        step(Tny2Vocabulary.END, position++);
        StringBuilder answer = new StringBuilder();
        float lowest = 1f;
        for (int picks = 0; position < Tny2Config.CONTEXT; position++, picks++) {
            int token = pick(creativity, random);
            if (picks < LOOKAHEAD) {
                lowest = Math.min(lowest, confidence);
            }
            if (token <= Tny2Vocabulary.END) {
                break;
            }
            answer.append(vocabulary.character(token));
            step(token, position);
        }
        return lowest < confidenceFloor ? UNKNOWN : answer.toString();
    }

    private void step(int token, int position) {
        QuantizedTable embedding = model.embedding();
        QuantizedTable positions = model.position();
        for (int i = 0; i < width; i++) {
            state[i] = embedding.weight(token, i) * embedding.scale(token)
                    + positions.weight(position, i) * positions.scale(position);
        }
        for (int l = 0; l < model.layers().size(); l++) {
            Tny2Layer layer = model.layers().get(l);
            normalize(state, layer.attentionGain(), normalized);
            layer.key().multiply(normalized, temporary);
            keyScales[l][position] = cache(keyCache[l], position * width, temporary);
            layer.value().multiply(normalized, temporary);
            valueScales[l][position] = cache(valueCache[l], position * width, temporary);
            layer.query().multiply(normalized, query);
            for (int h = 0; h < heads; h++) {
                attend(l, h, position);
            }
            layer.output().multiply(attended, temporary);
            for (int i = 0; i < width; i++) {
                state[i] += temporary[i];
            }
            normalize(state, layer.feedForwardGain(), normalized);
            layer.up().multiply(normalized, hidden);
            for (int i = 0; i < hidden.length; i++) {
                hidden[i] = Math.max(hidden[i], 0f);
            }
            layer.down().multiply(hidden, temporary);
            for (int i = 0; i < width; i++) {
                state[i] += temporary[i];
            }
        }
        normalize(state, model.finalGain(), normalized);
        embedding.multiply(normalized, scores);
    }

    private void attend(int layer, int head, int position) {
        int base = head * headWidth;
        float largest = -1e30f;
        for (int j = 0; j <= position; j++) {
            int key = j * width + base;
            float score = 0f;
            for (int i = 0; i < headWidth; i++) {
                score += query[base + i] * keyCache[layer][key + i];
            }
            score = score * keyScales[layer][j] / attentionDivisor;
            weights[j] = score;
            largest = Math.max(largest, score);
        }
        float sum = 0f;
        for (int j = 0; j <= position; j++) {
            weights[j] = weights[j] < EXP_LIMIT ? 0f : (float) Math.exp(weights[j] - largest);
            sum += weights[j];
        }
        for (int i = 0; i < headWidth; i++) {
            attended[base + i] = 0f;
        }
        for (int j = 0; j <= position; j++) {
            if (weights[j] == 0f) {
                continue;
            }
            int value = j * width + base;
            float scale = weights[j] * valueScales[layer][j] / sum;
            for (int i = 0; i < headWidth; i++) {
                attended[base + i] += scale * valueCache[layer][value + i];
            }
        }
    }

    private void normalize(float[] x, GainVector gain, float[] y) {
        float sum = 0f;
        for (int i = 0; i < width; i++) {
            sum += x[i] * x[i];
        }
        float scale = (float) (1.0 / Math.sqrt(sum / width + NORM_EPSILON));
        for (int i = 0; i < width; i++) {
            y[i] = x[i] * scale * gain.get(i);
        }
    }

    private float cache(byte[] target, int offset, float[] source) {
        float largest = 0f;
        for (int i = 0; i < width; i++) {
            largest = Math.max(largest, Math.abs(source[i]));
        }
        largest = Math.max(largest, CACHE_FLOOR);
        float factor = 127f / largest;
        for (int i = 0; i < width; i++) {
            target[offset + i] = QuantizedTable.level(source[i] * factor);
        }
        return largest / 127f;
    }

    private int pick(int creativity, RandomGenerator random) {
        int best = 0;
        for (int i = 1; i < Tny2Vocabulary.SIZE; i++) {
            if (scores[i] > scores[best]) {
                best = i;
            }
        }
        float sum = 0f;
        for (int i = 0; i < Tny2Vocabulary.SIZE; i++) {
            sum += (float) Math.exp(Math.clamp(scores[i] - scores[best], EXP_LIMIT, 0f));
        }
        confidence = 1f / sum;
        if (creativity == 0) {
            return best;
        }
        float temperature = creativity / CREATIVITY_STEP;
        float total = 0f;
        for (int i = 0; i < Tny2Vocabulary.SIZE; i++) {
            float weight = (float) Math.exp(Math.clamp((scores[i] - scores[best]) / temperature, EXP_LIMIT, 0f));
            weights[i] = weight < SAMPLE_FLOOR ? 0f : weight;
            total += weights[i];
        }
        float remaining = random.nextFloat() * total;
        for (int i = 0; i < Tny2Vocabulary.SIZE; i++) {
            remaining -= weights[i];
            if (remaining < 0 && weights[i] > 0) {
                return i;
            }
        }
        return best;
    }
}
