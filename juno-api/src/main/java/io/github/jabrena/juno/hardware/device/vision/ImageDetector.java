package io.github.jabrena.juno.hardware.device.vision;

/**
 * A small image model the board runs on grayscale frames, which scores how strongly a frame matches what the detector
 * was built to find.
 *
 * <p>Get a detector from its camera once, at startup: {@link io.github.jabrena.juno.hardware.device.camera.Camera#detector}.
 * Call {@link #begin()} once and check its result. Score frames from a buffer the program allocates once: {@link #score}
 * reads {@code inputSize() × inputSize()} bytes, one per pixel, row-major, with 0 as black and 255 as white. The score is an
 * integer from 0 to {@link #MAX_SCORE}, so the program needs no float math.
 */
public interface ImageDetector {

    /** The highest score {@link #score} returns: 1000 means the frame certainly matches the detector's target. */
    int MAX_SCORE = 1000;

    /** Loads the model and prepares the runtime, and returns whether it succeeded. */
    boolean begin();

    /** The side length, in pixels, of the square grayscale image {@link #score} reads. */
    int inputSize();

    /**
     * Runs the model on the first {@code inputSize() × inputSize()} bytes of {@code grayscale} and returns a score from 0 to
     * {@link #MAX_SCORE}, or {@code -1} when the detector is not started or {@code grayscale} is too short.
     */
    int score(byte[] grayscale);
}
