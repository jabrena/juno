package io.github.jabrena.juno.lm.training;

import io.github.jabrena.juno.lm.JlmConfig;
import io.github.jabrena.juno.lm.JlmFormatException;
import io.github.jabrena.juno.lm.JlmVocabulary;
import io.github.jabrena.juno.lm.corpus.Example;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Forward and backward pass of one example through the full-precision network, the same computation as the board
 * engine but without quantizing the attention cache. It keeps every activation of the sequence for the backward
 * pass, so one instance serves one worker thread and is reused for every example it trains.
 *
 * <p>Each block is {@code y = x + Wo * attention(rms(x) * g1)} then {@code x' = y + Wd * relu(Wu * rms(y) * g2)},
 * and the output is {@code E * rms(x) * gf} with the token embedding {@code E} tied to the input. The loss is the
 * summed cross-entropy of the answer characters and the closing {@code END}.
 */
final class SequencePass {
    private static final float NORM_EPSILON = 1e-5f;
    private static final double MIN_PROBABILITY = 1e-30;

    private final ParameterLayout layout;
    private final JlmVocabulary vocabulary;
    private final int context;
    private final int width;
    private final int heads;
    private final int headWidth;
    private final int hidden;
    private final float attentionScale;

    private final float[][] residual;
    private final float[][] middle;
    private final float[][] attentionNorm;
    private final float[][] attentionInput;
    private final float[][] queries;
    private final float[][] keys;
    private final float[][] values;
    private final float[][] probabilities;
    private final float[][] attended;
    private final float[][] feedForwardNorm;
    private final float[][] feedForwardInput;
    private final float[][] preActivation;
    private final float[] finalNorm;
    private final float[] finalInput;

    private final float[] dResidual;
    private final float[] dMiddle;
    private final float[] dNormalized;
    private final float[] dAttended;
    private final float[] dQuery;
    private final float[] dKey;
    private final float[] dValue;
    private final float[] activation;
    private final float[] dHidden;
    private final float[] dProbability;
    private final float[] logits;
    private final float dropout;
    private final RandomGenerator random;
    private final float[][] attentionKeep;
    private final float[][] feedForwardKeep;
    private final float[] dScaled;

    SequencePass(ParameterLayout layout) {
        this(layout, 0f, new SplittableRandom(0));
    }

    /**
     * A pass that drops {@code dropout} of each residual branch's outputs in training, scaling the rest by
     * {@code 1 / (1 - dropout)}; {@code random} picks them, so one seed per worker keeps training reproducible.
     */
    SequencePass(ParameterLayout layout, float dropout, RandomGenerator random) {
        this.dropout = dropout;
        this.random = random;
        this.layout = layout;
        JlmConfig config = layout.config();
        width = config.width();
        heads = config.heads();
        headWidth = config.headWidth();
        hidden = config.hidden();
        vocabulary = config.vocabulary();
        context = config.context();
        finalNorm = new float[context];
        dProbability = new float[context];
        logits = new float[vocabulary.size()];
        attentionScale = (float) (1.0 / Math.sqrt(headWidth));
        int layers = config.layers();
        int states = context * width;
        residual = new float[layers + 1][states];
        middle = new float[layers][states];
        attentionNorm = new float[layers][context];
        attentionInput = new float[layers][states];
        queries = new float[layers][states];
        keys = new float[layers][states];
        values = new float[layers][states];
        probabilities = new float[layers][heads * context * context];
        attended = new float[layers][states];
        feedForwardNorm = new float[layers][context];
        feedForwardInput = new float[layers][states];
        preActivation = new float[layers][context * hidden];
        finalInput = new float[states];
        dResidual = new float[states];
        dMiddle = new float[states];
        dNormalized = new float[states];
        dAttended = new float[states];
        dQuery = new float[states];
        dKey = new float[states];
        dValue = new float[states];
        activation = new float[hidden];
        attentionKeep = new float[layers][states];
        feedForwardKeep = new float[layers][states];
        dScaled = new float[width];
        dHidden = new float[hidden];
    }

    /**
     * Adds the gradient of the example's loss to {@code gradient} and returns the loss, summed over its
     * {@link #targets(Example)} predicted tokens.
     */
    double accumulate(Example example, float[] weights, float[] gradient) {
        int[] tokens = example.tokens(vocabulary);
        if (tokens.length > context) {
            throw new JlmFormatException("example needs %d positions, more than the context %d: \"%s\""
                    .formatted(tokens.length, context, example.question()));
        }
        int length = tokens.length - 1;
        forward(tokens, length, weights);
        Arrays.fill(dResidual, 0, length * width, 0f);
        Arrays.fill(dNormalized, 0, length * width, 0f);
        double loss = 0;
        Slice embedding = layout.embedding();
        for (int t = example.firstTarget() - 1; t < length; t++) {
            linear(weights, embedding, finalInput, t * width, logits, 0);
            softmax(logits);
            loss -= Math.log(Math.max(logits[tokens[t + 1]], MIN_PROBABILITY));
            logits[tokens[t + 1]] -= 1f;
            linearBackward(weights, gradient, embedding, finalInput, t * width, logits, 0, dNormalized, t * width);
        }
        int layers = layout.blocks().size();
        for (int t = 0; t < length; t++) {
            normalizeBackward(residual[layers], t * width, finalNorm[t], weights, gradient, layout.finalGain(),
                    dNormalized, dResidual);
        }
        for (int l = layers - 1; l >= 0; l--) {
            backwardBlock(l, length, weights, gradient);
        }
        Slice position = layout.position();
        for (int t = 0; t < length; t++) {
            int token = embedding.offset() + tokens[t] * width;
            int place = position.offset() + t * width;
            for (int i = 0; i < width; i++) {
                gradient[token + i] += dResidual[t * width + i];
                gradient[place + i] += dResidual[t * width + i];
            }
        }
        return loss;
    }

    /** The number of tokens whose prediction counts in the loss: the answer and its closing {@code END}. */
    static int targets(Example example) {
        return example.tokens().length - example.firstTarget();
    }

    private void forward(int[] tokens, int length, float[] weights) {
        Slice embedding = layout.embedding();
        Slice position = layout.position();
        for (int t = 0; t < length; t++) {
            int token = embedding.offset() + tokens[t] * width;
            int place = position.offset() + t * width;
            for (int i = 0; i < width; i++) {
                residual[0][t * width + i] = weights[token + i] + weights[place + i];
            }
        }
        for (int l = 0; l < layout.blocks().size(); l++) {
            forwardBlock(l, length, weights);
        }
        int last = layout.blocks().size();
        for (int t = 0; t < length; t++) {
            finalNorm[t] = normalize(residual[last], t * width, weights, layout.finalGain(), finalInput);
        }
    }

    private void forwardBlock(int l, int length, float[] weights) {
        ParameterLayout.Block block = layout.blocks().get(l);
        for (int t = 0; t < length; t++) {
            int at = t * width;
            attentionNorm[l][t] = normalize(residual[l], at, weights, block.attentionGain(), attentionInput[l]);
            linear(weights, block.query(), attentionInput[l], at, queries[l], at);
            linear(weights, block.key(), attentionInput[l], at, keys[l], at);
            linear(weights, block.value(), attentionInput[l], at, values[l], at);
        }
        for (int t = 0; t < length; t++) {
            for (int h = 0; h < heads; h++) {
                attend(l, h, t);
            }
        }
        for (int t = 0; t < length; t++) {
            int at = t * width;
            linear(weights, block.output(), attended[l], at, middle[l], at);
            for (int i = 0; i < width; i++) {
                middle[l][at + i] = middle[l][at + i] * keep(attentionKeep[l], at + i) + residual[l][at + i];
            }
            feedForwardNorm[l][t] = normalize(middle[l], at, weights, block.feedForwardGain(), feedForwardInput[l]);
            linear(weights, block.up(), feedForwardInput[l], at, preActivation[l], t * hidden);
            for (int i = 0; i < hidden; i++) {
                activation[i] = Math.max(preActivation[l][t * hidden + i], 0f);
            }
            linear(weights, block.down(), activation, 0, residual[l + 1], at);
            for (int i = 0; i < width; i++) {
                residual[l + 1][at + i] = residual[l + 1][at + i] * keep(feedForwardKeep[l], at + i)
                        + middle[l][at + i];
            }
        }
    }

    private void attend(int l, int h, int t) {
        int base = h * headWidth;
        int row = (h * context + t) * context;
        float[] p = probabilities[l];
        float largest = Float.NEGATIVE_INFINITY;
        for (int j = 0; j <= t; j++) {
            float score = dot(queries[l], t * width + base, keys[l], j * width + base) * attentionScale;
            p[row + j] = score;
            largest = Math.max(largest, score);
        }
        float sum = 0f;
        for (int j = 0; j <= t; j++) {
            p[row + j] = (float) Math.exp(p[row + j] - largest);
            sum += p[row + j];
        }
        int out = t * width + base;
        Arrays.fill(attended[l], out, out + headWidth, 0f);
        for (int j = 0; j <= t; j++) {
            p[row + j] /= sum;
            int value = j * width + base;
            for (int i = 0; i < headWidth; i++) {
                attended[l][out + i] += p[row + j] * values[l][value + i];
            }
        }
    }

    /** On entry {@link #dResidual} is the gradient of the block's output; on exit, of its input. */
    private void backwardBlock(int l, int length, float[] weights, float[] gradient) {
        ParameterLayout.Block block = layout.blocks().get(l);
        int states = length * width;
        System.arraycopy(dResidual, 0, dMiddle, 0, states);
        Arrays.fill(dNormalized, 0, states, 0f);
        for (int t = 0; t < length; t++) {
            int at = t * width;
            for (int i = 0; i < hidden; i++) {
                activation[i] = Math.max(preActivation[l][t * hidden + i], 0f);
            }
            Arrays.fill(dHidden, 0f);
            for (int i = 0; i < width; i++) {
                dScaled[i] = dResidual[at + i] * feedForwardKeep[l][at + i];
            }
            linearBackward(weights, gradient, block.down(), activation, 0, dScaled, 0, dHidden, 0);
            for (int i = 0; i < hidden; i++) {
                if (preActivation[l][t * hidden + i] <= 0f) {
                    dHidden[i] = 0f;
                }
            }
            linearBackward(weights, gradient, block.up(), feedForwardInput[l], at, dHidden, 0, dNormalized, at);
            normalizeBackward(middle[l], at, feedForwardNorm[l][t], weights, gradient, block.feedForwardGain(),
                    dNormalized, dMiddle);
        }
        System.arraycopy(dMiddle, 0, dResidual, 0, states);
        Arrays.fill(dAttended, 0, states, 0f);
        for (int t = 0; t < length; t++) {
            for (int i = 0; i < width; i++) {
                dScaled[i] = dMiddle[t * width + i] * attentionKeep[l][t * width + i];
            }
            linearBackward(weights, gradient, block.output(), attended[l], t * width, dScaled, 0, dAttended,
                    t * width);
        }
        Arrays.fill(dQuery, 0, states, 0f);
        Arrays.fill(dKey, 0, states, 0f);
        Arrays.fill(dValue, 0, states, 0f);
        for (int t = 0; t < length; t++) {
            for (int h = 0; h < heads; h++) {
                attendBackward(l, h, t);
            }
        }
        Arrays.fill(dNormalized, 0, states, 0f);
        for (int t = 0; t < length; t++) {
            int at = t * width;
            linearBackward(weights, gradient, block.query(), attentionInput[l], at, dQuery, at, dNormalized, at);
            linearBackward(weights, gradient, block.key(), attentionInput[l], at, dKey, at, dNormalized, at);
            linearBackward(weights, gradient, block.value(), attentionInput[l], at, dValue, at, dNormalized, at);
            normalizeBackward(residual[l], at, attentionNorm[l][t], weights, gradient, block.attentionGain(),
                    dNormalized, dResidual);
        }
    }

    private void attendBackward(int l, int h, int t) {
        int base = h * headWidth;
        int row = (h * context + t) * context;
        float[] p = probabilities[l];
        int out = t * width + base;
        float weighted = 0f;
        for (int j = 0; j <= t; j++) {
            int value = j * width + base;
            dProbability[j] = dot(dAttended, out, values[l], value);
            weighted += p[row + j] * dProbability[j];
            for (int i = 0; i < headWidth; i++) {
                dValue[value + i] += p[row + j] * dAttended[out + i];
            }
        }
        for (int j = 0; j <= t; j++) {
            float dScore = p[row + j] * (dProbability[j] - weighted) * attentionScale;
            int key = j * width + base;
            for (int i = 0; i < headWidth; i++) {
                dQuery[out + i] += dScore * keys[l][key + i];
                dKey[key + i] += dScore * queries[l][out + i];
            }
        }
    }

    /** Draws whether one branch output survives dropout, records its scale for the backward pass and returns it. */
    private float keep(float[] scales, int index) {
        float scale = dropout == 0f ? 1f : random.nextFloat() < dropout ? 0f : 1f / (1f - dropout);
        scales[index] = scale;
        return scale;
    }

    private float dot(float[] a, int aOffset, float[] b, int bOffset) {
        float sum = 0f;
        for (int i = 0; i < headWidth; i++) {
            sum += a[aOffset + i] * b[bOffset + i];
        }
        return sum;
    }

    /** {@code y = x * r * gain} with {@code r = 1 / sqrt(mean(x^2) + epsilon)}; returns {@code r}. */
    private float normalize(float[] x, int offset, float[] weights, Slice gain, float[] y) {
        float sum = 0f;
        for (int i = 0; i < width; i++) {
            sum += x[offset + i] * x[offset + i];
        }
        float r = (float) (1.0 / Math.sqrt(sum / width + NORM_EPSILON));
        for (int i = 0; i < width; i++) {
            y[offset + i] = x[offset + i] * r * weights[gain.offset() + i];
        }
        return r;
    }

    private void normalizeBackward(float[] x, int offset, float r, float[] weights, float[] gradient, Slice gain,
            float[] dy, float[] dx) {
        float dot = 0f;
        for (int i = 0; i < width; i++) {
            dot += dy[offset + i] * weights[gain.offset() + i] * x[offset + i];
        }
        float correction = r * r * r * dot / width;
        for (int i = 0; i < width; i++) {
            float g = weights[gain.offset() + i];
            gradient[gain.offset() + i] += dy[offset + i] * x[offset + i] * r;
            dx[offset + i] += r * dy[offset + i] * g - correction * x[offset + i];
        }
    }

    /** {@code y[r] = sum_c W[r][c] * x[c]} for the matrix at {@code slice}. */
    private static void linear(float[] weights, Slice slice, float[] x, int xOffset, float[] y, int yOffset) {
        int cols = slice.cols();
        int index = slice.offset();
        for (int r = 0; r < slice.rows(); r++) {
            float sum = 0f;
            for (int c = 0; c < cols; c++) {
                sum += weights[index + c] * x[xOffset + c];
            }
            y[yOffset + r] = sum;
            index += cols;
        }
    }

    /** Adds {@code dy (x) x} to the matrix gradient and {@code W^T * dy} to {@code dx}. */
    private static void linearBackward(float[] weights, float[] gradient, Slice slice, float[] x, int xOffset,
            float[] dy, int dyOffset, float[] dx, int dxOffset) {
        int cols = slice.cols();
        int index = slice.offset();
        for (int r = 0; r < slice.rows(); r++, index += cols) {
            float d = dy[dyOffset + r];
            if (d == 0f) {
                continue;
            }
            for (int c = 0; c < cols; c++) {
                gradient[index + c] += d * x[xOffset + c];
                dx[dxOffset + c] += weights[index + c] * d;
            }
        }
    }

    /** Turns logits into probabilities in place. */
    private static void softmax(float[] logits) {
        float largest = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            largest = Math.max(largest, logit);
        }
        float sum = 0f;
        for (int i = 0; i < logits.length; i++) {
            logits[i] = (float) Math.exp(logits[i] - largest);
            sum += logits[i];
        }
        for (int i = 0; i < logits.length; i++) {
            logits[i] /= sum;
        }
    }
}
