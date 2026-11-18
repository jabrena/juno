package io.github.jabrena.juno.hardware.device.vision;

/**
 * The cat detector: an {@link ImageDetector} whose score is how likely a grayscale frame shows a cat. Get it with
 * {@code camera.detector(DetectorKind.CAT)} on the AI Thinker ESP32-CAM.
 */
public interface CatDetector extends ImageDetector {
}
