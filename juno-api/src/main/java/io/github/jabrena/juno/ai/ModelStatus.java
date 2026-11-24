package io.github.jabrena.juno.ai;

/** The outcome of {@link LanguageModel}'s {@code begin} methods and of {@link LanguageModel#ask(String, int)}. */
public enum ModelStatus {
    /** The model was loaded, or the question was accepted and its answer can be read. */
    OK,
    /** The engine lost track of the PSRAM copy it was given; a correct program never sees it. */
    INVALID_HANDLE,
    /** {@code begin}: the model is not a complete TNY2 file the engine accepts. */
    INVALID_MODEL,
    /** {@code begin}: PSRAM has no room for the weights and the attention cache. */
    EXHAUSTED,
    /** {@link LanguageModel#ask(String, int)}: no model is loaded. */
    NOT_LOADED,
    /**
     * {@link LanguageModel#ask(String, int)}: the question is empty or the creativity is outside 0 to
     * {@link LanguageModel#MAX_CREATIVITY}.
     */
    INVALID_ARGUMENT,
    /** {@code begin} from an SD card: the file is missing, empty or could not be read. */
    UNREADABLE
}
