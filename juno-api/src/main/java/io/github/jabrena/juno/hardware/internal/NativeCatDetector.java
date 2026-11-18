package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.device.vision.CatDetector;

/**
 * The AI Thinker ESP32-CAM's cat detector: {@link #begin()} and the native score are intrinsics the compiler lowers to
 * the bundled TensorFlow Lite Micro runtime, so they have no Java body and cannot run, or be mocked, on a desktop JVM.
 * Mock {@link CatDetector} instead. The board has one detector, so every instance runs the same model.
 */
public final class NativeCatDetector implements CatDetector {
    /** The first model's input side length, in pixels. */
    private static final int INPUT_SIZE = 64;
    private static final NativeCatDetector INSTANCE = new NativeCatDetector();

    private NativeCatDetector() {
    }

    /** The board's cat detector, the same object on every call. */
    public static CatDetector catDetector() {
        return INSTANCE;
    }

    @Override
    public native boolean begin();

    @Override
    public int inputSize() {
        return INPUT_SIZE;
    }

    @Override
    public int score(byte[] grayscale) {
        return score(grayscale, grayscale.length);
    }

    /** The native score over the first {@code capacity} bytes of {@code grayscale}; see {@link CatDetector#score}. */
    native int score(byte[] grayscale, int capacity);
}
