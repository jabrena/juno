package io.github.jabrena.juno.hardware.ai;

/**
 * A TNY2 character-level language model held in PSRAM, answering one question at a time, one character at a time.
 *
 * <p>Take the model from its board once, at startup, with
 * {@link io.github.jabrena.juno.hardware.MCU#getLanguageModel()}. Copy the TNY2 file into a
 * {@link io.github.jabrena.juno.hardware.Psram} buffer (for example from a generated {@code Tny2ModelImage} in flash),
 * {@link #load(int)} it, and release the buffer: the model keeps its own PSRAM copy of the weights and its attention
 * cache. Then {@link #ask(String, int)} a question and call {@link #nextChar()} until it returns {@link #END}. Each
 * character is handed over as soon as it is generated, so the same loop can print it to the serial port, write it to
 * an HTTP response or send it over TCP, and nothing is allocated per answer.
 *
 * <p>A question is printable ASCII, at most {@link #MAX_QUESTION} characters; longer text is cut. The engine reads it
 * the way the model was trained: in lower case, with {@code ? ! , ;} and quotes as spaces and runs of spaces collapsed,
 * so wordings that differ only in case or punctuation are the same question. Asking runs the question through the
 * model, which takes about 30 ms per character on the Nano ESP32, and also picks the answer's first 8 characters: if
 * the model is unsure of any of them, the answer is "I do not know." instead of a guess. Each later
 * {@link #nextChar()} takes about the same 30 ms.
 */
public interface LanguageModel {
    /** Success of {@link #load(int)}, {@link #ask(String, int)} and {@link #release()}. */
    int OK = 0;

    /** {@link #load(int)}: the PSRAM handle is unknown or released. */
    int ERROR_INVALID_HANDLE = -1;

    /** {@link #load(int)}: the buffer is not a complete TNY2 model the engine accepts. */
    int ERROR_INVALID_MODEL = -2;

    /** {@link #load(int)}: PSRAM has no room for the weights and the attention cache. */
    int ERROR_EXHAUSTED = -3;

    /** {@link #ask(String, int)}: no model is loaded. */
    int ERROR_NOT_LOADED = -4;

    /** {@link #ask(String, int)}: the question is empty or the creativity is outside 0 to {@link #MAX_CREATIVITY}. */
    int ERROR_INVALID_ARGUMENT = -5;

    /** What {@link #nextChar()} returns when the answer is complete or no question was asked. */
    int END = -1;

    /** Longest question the model reads. */
    int MAX_QUESTION = 72;

    /** Highest creativity; 0 always picks the most likely character. */
    int MAX_CREATIVITY = 9;

    /**
     * Copies and validates the TNY2 model in the PSRAM buffer {@code psramHandle}, replacing any model loaded before.
     * Returns {@link #OK} or a negative error.
     */
    int load(int psramHandle);

    /** Whether a model is loaded. */
    boolean isLoaded();

    /** The loaded model's embedding width, or 0. */
    int width();

    /** The loaded model's number of transformer layers, or 0. */
    int layers();

    /**
     * Starts answering {@code question}, discarding any answer in progress. Creativity 0 picks the most likely
     * character every time; up to {@link #MAX_CREATIVITY} samples among the likely ones. Returns {@link #OK} or a
     * negative error.
     */
    int ask(String question, int creativity);

    /** Like {@link #ask(String, int)}, for the first {@code length} bytes of {@code question}, such as a request body. */
    int ask(byte[] question, int length, int creativity);

    /** The answer's next character, or {@link #END} when it is complete. */
    int nextChar();

    /** Frees the model's PSRAM; {@link #isLoaded()} is false afterwards. Returns {@link #OK}. */
    int release();
}
