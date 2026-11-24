package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;

/**
 * The board's TNY2 engine behind {@link io.github.jabrena.juno.hardware.MCU#getLanguageModel()}: one model per
 * program, in PSRAM, so the calls never read the object they are called on.
 */
public final class NativeLanguageModel implements LanguageModel {
    private static final NativeLanguageModel INSTANCE = new NativeLanguageModel();

    private NativeLanguageModel() {
    }

    /** The language model the MCU facade hands out. */
    public static LanguageModel languageModel() {
        return INSTANCE;
    }

    @Override
    public ModelStatus load(int psramHandle) {
        return status(loadModel(psramHandle));
    }

    @Override
    public native boolean isLoaded();

    @Override
    public native int width();

    @Override
    public native int layers();

    @Override
    public ModelStatus ask(String question, int creativity) {
        return status(askText(question, creativity));
    }

    @Override
    public ModelStatus ask(byte[] question, int length, int creativity) {
        return status(askBytes(question, question.length, length, creativity));
    }

    @Override
    public native int nextChar();

    @Override
    public void release() {
        releaseModel();
    }

    /** The engine's result code as a {@link ModelStatus}: 0 is {@link ModelStatus#OK}, -1 to -5 the errors in order. */
    static ModelStatus status(int code) {
        return switch (code) {
            case 0 -> ModelStatus.OK;
            case -1 -> ModelStatus.INVALID_HANDLE;
            case -2 -> ModelStatus.INVALID_MODEL;
            case -3 -> ModelStatus.EXHAUSTED;
            case -4 -> ModelStatus.NOT_LOADED;
            default -> ModelStatus.INVALID_ARGUMENT;
        };
    }

    native int loadModel(int psramHandle);

    native int askText(String question, int creativity);

    /** Native overload carrying the Java array capacity for complete bounds validation. */
    native int askBytes(byte[] question, int capacity, int length, int creativity);

    native int releaseModel();
}
