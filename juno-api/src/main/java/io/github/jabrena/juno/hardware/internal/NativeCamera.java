package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.device.camera.Camera;

/**
 * The AI Thinker ESP32-CAM's camera: {@link #begin()} and the native capture are intrinsics the compiler lowers to the
 * core's {@code esp_camera} driver, so they have no Java body and cannot run, or be mocked, on a desktop JVM. Mock
 * {@link Camera} instead. The board has one camera, so every instance drives the same sensor.
 */
public final class NativeCamera implements Camera {
    private static final NativeCamera INSTANCE = new NativeCamera();

    private NativeCamera() {
    }

    /** The board's camera, the same object on every call. */
    public static Camera camera() {
        return INSTANCE;
    }

    @Override
    public native boolean begin();

    @Override
    public int capture(byte[] jpeg) {
        return capture(jpeg, jpeg.length);
    }

    /** The native capture into {@code capacity} bytes of {@code jpeg}; see {@link Camera#capture(byte[])}. */
    native int capture(byte[] jpeg, int capacity);
}
