package io.github.jabrena.juno.ai;

/** The outcome of {@link LanguageModel#load(int)} and {@link LanguageModel#ask(String, int)}. */
public enum ModelStatus {
    /** The model was loaded, or the question was accepted and its answer can be read. */
    OK,
    /** {@link LanguageModel#load(int)}: the PSRAM handle is unknown or released. */
    INVALID_HANDLE,
    /** {@link LanguageModel#load(int)}: the buffer is not a complete TNY2 model the engine accepts. */
    INVALID_MODEL,
    /** {@link LanguageModel#load(int)}: PSRAM has no room for the weights and the attention cache. */
    EXHAUSTED,
    /** {@link LanguageModel#ask(String, int)}: no model is loaded. */
    NOT_LOADED,
    /**
     * {@link LanguageModel#ask(String, int)}: the question is empty or the creativity is outside 0 to
     * {@link LanguageModel#MAX_CREATIVITY}.
     */
    INVALID_ARGUMENT
}
