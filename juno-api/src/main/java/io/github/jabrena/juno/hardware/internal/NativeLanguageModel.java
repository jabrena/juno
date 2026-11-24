package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.LanguageModel;

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
    public native int load(int psramHandle);

    @Override
    public native boolean isLoaded();

    @Override
    public native int width();

    @Override
    public native int layers();

    @Override
    public native int ask(String question, int creativity);

    @Override
    public int ask(byte[] question, int length, int creativity) {
        return ask(question, question.length, length, creativity);
    }

    @Override
    public native int nextChar();

    @Override
    public native int release();

    /** Native overload carrying the Java array capacity for complete bounds validation. */
    native int ask(byte[] question, int capacity, int length, int creativity);
}
