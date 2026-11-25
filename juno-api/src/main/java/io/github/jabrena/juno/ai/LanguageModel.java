package io.github.jabrena.juno.ai;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

/**
 * A JLM1 character-level language model held in PSRAM, answering one question at a time, one character at a time.
 *
 * <p>Take the model from its board once, at startup, with
 * {@link io.github.jabrena.juno.hardware.MCU#getLanguageModel()}, and {@link #begin()} it: the build links the JLM1
 * file named by {@code -Djuno.lm.model} into the firmware (such as {@code juno-lm}'s JUNO.BIN, trained from the Juno
 * facts), so one {@code juno:flash} uploads the program and its model together, and the engine copies it into PSRAM
 * with its attention cache. {@link #begin(SdCard, String)} loads a JLM1 file from an SD card instead, so a model can
 * change without reflashing. Then {@link #ask(String, int)} a question and call {@link #nextChar()} until it returns
 * {@link #END}. Each character is handed over as soon as it is generated, so the same loop can print it to the serial
 * port, write it to an HTTP response or send it over TCP, and nothing is allocated per answer.
 *
 * <p>A question is printable ASCII, at most {@link #MAX_QUESTION} characters; longer text is cut. The engine reads it
 * the way the model was trained: in lower case, with {@code ? ! , ;} and quotes as spaces and runs of spaces collapsed,
 * so wordings that differ only in case or punctuation are the same question. Asking runs the question through the
 * model, which takes about 30 ms per character on the Nano ESP32, and also picks the answer's first 8 characters: if
 * the model is unsure of any of them, the answer is "I do not know." instead of a guess. Each later
 * {@link #nextChar()} takes about the same 30 ms.
 */
public interface LanguageModel {
    /** What {@link #nextChar()} returns when the answer is complete or no question was asked. */
    int END = -1;

    /** Longest question the model reads. */
    int MAX_QUESTION = 72;

    /** Highest creativity; 0 always picks the most likely character. */
    int MAX_CREATIVITY = 9;

    /**
     * Loads the JLM1 model the build linked into the firmware, replacing any model loaded before. A program that calls
     * this method must be built with {@code -Djuno.lm.model=<file>}, or the build fails. Returns {@link ModelStatus#OK},
     * or why it could not.
     */
    ModelStatus begin();

    /**
     * Loads the JLM1 file at {@code path} on {@code card}, which must already be mounted with its own
     * {@link SdCard#begin()}, replacing any model loaded before. The file is copied into PSRAM through a 1 KiB buffer,
     * so call it once, at startup. Returns {@link ModelStatus#OK}, {@link ModelStatus#UNREADABLE} for a missing or
     * unreadable file, or why the engine refused it.
     */
    ModelStatus begin(SdCard card, String path);

    /** Whether a model is loaded. */
    boolean isLoaded();

    /** The loaded model's embedding width, or 0. */
    int width();

    /** The loaded model's number of transformer layers, or 0. */
    int layers();

    /**
     * Starts answering {@code question}, discarding any answer in progress. Creativity 0 picks the most likely
     * character every time; up to {@link #MAX_CREATIVITY} samples among the likely ones. Returns {@link ModelStatus#OK},
     * or why the question was not accepted.
     */
    ModelStatus ask(String question, int creativity);

    /** Like {@link #ask(String, int)}, for the first {@code length} bytes of {@code question}, such as a request body. */
    ModelStatus ask(byte[] question, int length, int creativity);

    /** The answer's next character, or {@link #END} when it is complete. */
    int nextChar();

    /** Frees the model's PSRAM; {@link #isLoaded()} is false afterwards. */
    void release();
}
