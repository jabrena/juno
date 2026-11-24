package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * The board's JLM1 engine behind {@link io.github.jabrena.juno.hardware.MCU#getLanguageModel()}, in Java: it reads the
 * model where it was loaded ({@link JlmLayout}), keeps its attention cache in a second block, and runs the transformer
 * one position at a time, the computation of {@code JlmInference} in {@code juno-lm} step for step. Java decides
 * everything: the file layout, the tokens, the layer and head loops, the picks and the fallback; the loops over float
 * arrays run in {@link ModelMemory} and {@link JlmRuntime}, which are native on the board. Every buffer is allocated
 * once, at its largest size, so asking and answering allocate nothing.
 */
public final class JlmLanguageModel implements LanguageModel {
    // No field post-increment inside an expression and no compound assignment to an array element: javac compiles
    // them to dup_x1 and dup2, which are outside the bytecode subset Juno accepts.
    static final int END_TOKEN = 1;
    static final int LOOKAHEAD = 8;
    static final float CONFIDENCE_FLOOR = 0.5f;
    static final String UNKNOWN = "I do not know.";
    private static final int CHUNK_BYTES = 1024;

    /**
     * The board's engine, created on the first {@link #languageModel()} call rather than in a static initializer: Juno
     * runs static initializers in a fixed order, so a program's {@code static final LanguageModel MODEL =
     * MCU.getLanguageModel()} could otherwise read it before it exists.
     */
    private static @Nullable JlmLanguageModel instance;

    private final JlmRuntime runtime;
    private final JlmLayout layout = new JlmLayout();
    private final JlmQuestion question = new JlmQuestion();
    private final JlmSampler sampler;
    private final float[] state = new float[JlmLayout.MAX_WIDTH];
    private final float[] normalized = new float[JlmLayout.MAX_WIDTH];
    private final float[] query = new float[JlmLayout.MAX_WIDTH];
    private final float[] temporary = new float[JlmLayout.MAX_WIDTH];
    private final float[] attended = new float[JlmLayout.MAX_WIDTH];
    private final float[] hiddenValues = new float[JlmLayout.MAX_HIDDEN];
    private final float[] weights = new float[JlmLayout.MAX_CONTEXT];
    private final float[] scores = new float[JlmLayout.MAX_VOCAB];
    private final float[] one = {1f};
    private final char[] pending = new char[LOOKAHEAD];
    private final byte[] chunk = new byte[CHUNK_BYTES];

    private @Nullable ModelMemory model;
    private @Nullable ModelMemory cache;
    private float attentionDivisor;

    private boolean answering;
    private int answerPosition;
    private int pendingLength;
    private int pendingNext;
    private int creativity;
    private boolean pendingEnded;
    private int fallbackNext = -1;

    /** An engine over {@code runtime}; the board's one engine is {@link #languageModel()}. */
    public JlmLanguageModel(JlmRuntime runtime) {
        this.runtime = runtime;
        this.sampler = new JlmSampler(runtime);
    }

    /** The language model the MCU facade hands out. */
    public static LanguageModel languageModel() {
        JlmLanguageModel engine = instance;
        if (engine == null) {
            engine = new JlmLanguageModel(new PsramJlmRuntime());
            instance = engine;
        }
        return engine;
    }

    @Override
    public ModelStatus begin() {
        release();
        ModelMemory bundled = runtime.bundledModel();
        return bundled == null ? ModelStatus.EXHAUSTED : load(bundled);
    }

    @Override
    public ModelStatus begin(SdCard card, String path) {
        release();
        int size = card.size(path);
        if (size <= 0) {
            return ModelStatus.UNREADABLE;
        }
        ModelMemory file = runtime.allocate(size);
        if (file == null) {
            return ModelStatus.EXHAUSTED;
        }
        boolean copied;
        try (InputStream input = card.open(path)) {
            copied = copy(input, size, file);
        } catch (IOException unreadable) {
            copied = false;
        }
        if (!copied) {
            file.release();
            return ModelStatus.UNREADABLE;
        }
        return load(file);
    }

    /** Reads {@code size} bytes of {@code input} 1 KiB at a time into {@code target}; false when it is missing or short. */
    boolean copy(@Nullable InputStream input, int size, ModelMemory target) throws IOException {
        if (input == null) {
            return false;
        }
        int offset = 0;
        while (offset < size) {
            int read = input.read(chunk, 0, Math.min(CHUNK_BYTES, size - offset));
            if (read <= 0) {
                return false;
            }
            target.write(offset, chunk, read);
            offset += read;
        }
        return true;
    }

    /** Reads the JLM1 file in {@code file}, which this engine then owns, and allocates its attention cache. */
    private ModelStatus load(ModelMemory file) {
        if (!layout.read(file)) {
            file.release();
            return ModelStatus.INVALID_MODEL;
        }
        ModelMemory attention = runtime.allocate(layout.cacheBytes());
        if (attention == null) {
            file.release();
            return ModelStatus.EXHAUSTED;
        }
        attentionDivisor = (float) Math.sqrt(layout.headWidth());
        model = file;
        cache = attention;
        return ModelStatus.OK;
    }

    @Override
    public boolean isLoaded() {
        return model != null;
    }

    @Override
    public int width() {
        return model == null ? 0 : layout.width();
    }

    @Override
    public int layers() {
        return model == null ? 0 : layout.layers();
    }

    @Override
    public ModelStatus ask(String text, int creativity) {
        question.read(text);
        return answer(creativity);
    }

    @Override
    public ModelStatus ask(byte[] text, int length, int creativity) {
        if (length < 0 || length > text.length) {
            return ModelStatus.INVALID_ARGUMENT;
        }
        question.read(text, length);
        return answer(creativity);
    }

    /** Runs the normalized question through the model and picks the answer's first characters. */
    private ModelStatus answer(int creativity) {
        if (model == null) {
            return ModelStatus.NOT_LOADED;
        }
        int size = question.length();
        if (creativity < 0 || creativity > MAX_CREATIVITY || size == 0) {
            return ModelStatus.INVALID_ARGUMENT;
        }
        this.creativity = creativity;
        int context = layout.context();
        int position = 0;
        for (; position < size; position++) {
            step(layout.token(question.charAt(position)), position);
        }
        step(END_TOKEN, position++);
        pendingLength = 0;
        pendingNext = 0;
        pendingEnded = false;
        fallbackNext = -1;
        float lowest = 1f;
        for (int picks = 0; picks < LOOKAHEAD && position < context; picks++, position++) {
            int token = sampler.pick(scores, layout.vocabulary(), creativity);
            lowest = Math.min(lowest, sampler.confidence());
            if (token <= END_TOKEN) {
                pendingEnded = true;
                break;
            }
            pending[pendingLength] = layout.character(token);
            pendingLength++;
            step(token, position);
        }
        if (position >= context) {
            pendingEnded = true;
        }
        if (lowest < CONFIDENCE_FLOOR) {
            fallbackNext = 0;
        }
        answerPosition = position;
        answering = true;
        return ModelStatus.OK;
    }

    @Override
    public int nextChar() {
        if (!answering) {
            return END;
        }
        if (fallbackNext >= 0) {
            return nextFallbackChar();
        }
        if (pendingNext < pendingLength) {
            int index = pendingNext;
            pendingNext = index + 1;
            return pending[index];
        }
        if (pendingEnded || answerPosition >= layout.context()) {
            answering = false;
            return END;
        }
        int token = sampler.pick(scores, layout.vocabulary(), creativity);
        if (token <= END_TOKEN) {
            answering = false;
            return END;
        }
        int position = answerPosition;
        answerPosition = position + 1;
        step(token, position);
        return layout.character(token);
    }

    private int nextFallbackChar() {
        if (fallbackNext == UNKNOWN.length()) {
            answering = false;
            return END;
        }
        int index = fallbackNext;
        fallbackNext = index + 1;
        return UNKNOWN.charAt(index);
    }

    @Override
    public void release() {
        if (model != null) {
            model.release();
        }
        if (cache != null) {
            cache.release();
        }
        model = null;
        cache = null;
        answering = false;
    }

    /** Feeds {@code token} at {@code position}, leaving the next token's scores in {@link #scores}. */
    private void step(int token, int position) {
        ModelMemory weightsMemory = model;
        ModelMemory attention = cache;
        if (weightsMemory == null || attention == null) {
            return;
        }
        int width = layout.width();
        int stride = layout.rowStride();
        for (int i = 0; i < width; i++) {
            state[i] = 0f;
        }
        weightsMemory.accumulateRows(layout.embeddingOffset() + token * stride, stride, 1, 0, width, one, 1f, state,
                0);
        weightsMemory.accumulateRows(layout.positionOffset() + position * stride, stride, 1, 0, width, one, 1f, state,
                0);
        for (int layer = 0; layer < layout.layers(); layer++) {
            attentionBlock(weightsMemory, attention, layer, position);
            feedForwardBlock(weightsMemory, layer);
        }
        runtime.rmsNorm(state, layout.gains(), layout.finalGain(), normalized, width);
        weightsMemory.multiplyRows(layout.embeddingOffset(), stride, layout.vocabulary(), 0, width, normalized, 0,
                scores);
    }

    /** Caches this position's key and value, attends with every head, and adds the result to {@link #state}. */
    private void attentionBlock(ModelMemory weightsMemory, ModelMemory attention, int layer, int position) {
        int width = layout.width();
        int stride = layout.rowStride();
        int headWidth = layout.headWidth();
        runtime.rmsNorm(state, layout.gains(), layout.attentionGain(layer), normalized, width);
        weightsMemory.multiplyRows(layout.keyOffset(layer), stride, width, 0, width, normalized, 0, temporary);
        attention.quantizeRow(layout.keyCacheOffset(layer) + position * stride, temporary, width);
        weightsMemory.multiplyRows(layout.valueOffset(layer), stride, width, 0, width, normalized, 0, temporary);
        attention.quantizeRow(layout.valueCacheOffset(layer) + position * stride, temporary, width);
        weightsMemory.multiplyRows(layout.queryOffset(layer), stride, width, 0, width, normalized, 0, query);
        for (int head = 0; head < layout.heads(); head++) {
            attention.attend(layout.keyCacheOffset(layer), layout.valueCacheOffset(layer), stride, position + 1,
                    head * headWidth, headWidth, query, weights, attentionDivisor, attended);
        }
        weightsMemory.multiplyRows(layout.outputOffset(layer), stride, width, 0, width, attended, 0, temporary);
        runtime.add(state, temporary, width);
    }

    /** The feed-forward block of {@code layer}: norm, up, ReLU, down, added to {@link #state}. */
    private void feedForwardBlock(ModelMemory weightsMemory, int layer) {
        int width = layout.width();
        int hidden = layout.hidden();
        runtime.rmsNorm(state, layout.gains(), layout.feedForwardGain(layer), normalized, width);
        weightsMemory.multiplyRows(layout.upOffset(layer), layout.rowStride(), hidden, 0, width, normalized, 0,
                hiddenValues);
        runtime.relu(hiddenValues, hidden);
        weightsMemory.multiplyRows(layout.downOffset(layer), layout.downStride(), width, 0, hidden, hiddenValues, 0,
                temporary);
        runtime.add(state, temporary, width);
    }
}
