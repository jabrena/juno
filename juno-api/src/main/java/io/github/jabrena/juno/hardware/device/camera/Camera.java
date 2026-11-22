package io.github.jabrena.juno.hardware.device.camera;

/**
 * A still-image camera that delivers each frame as JPEG bytes into a buffer the program owns.
 *
 * <p>Take the camera from its board once, at startup: {@link io.github.jabrena.juno.hardware.MCU#getCamera()} for
 * the AI Thinker ESP32-CAM's OV2640. Call {@link #begin()} once before capturing and check its result before starting
 * networking or a capture loop. On the ESP32-CAM the native camera driver owns its PSRAM frame buffers; each
 * {@link #capture(byte[])} waits for the next frame, copies it into the caller-owned buffer and returns the driver frame
 * immediately, so the program allocates the Java buffer once and reuses it in its loop. A frame that does not fit the
 * buffer is reported as a failure rather than truncated.
 *
 * <p>The ESP32-CAM's white flash LED is not part of the camera: it is GPIO4, driven as an ordinary output through
 * {@link io.github.jabrena.juno.hardware.MCU#getGpio()} with {@code digitalPin("D4")}. Turn it on before
 * {@link #capture(byte[])} and off afterwards, and drop one frame first, since the frame already queued was exposed
 * without it.
 */
public interface Camera {
    /** Starts the camera at {@link FrameSize#QQVGA} and returns whether it initialized. */
    boolean begin();

    /**
     * Waits for the next frame and copies its JPEG bytes into {@code jpeg} from index 0. Returns the frame's length in
     * bytes, or {@code -1} when the camera is not started, no frame arrived, or the frame is larger than {@code jpeg}.
     */
    int capture(byte[] jpeg);

    /**
     * Switches the sensor to {@code frameSize} and drops the frames already taken at the old size, so the next
     * {@link #capture(byte[])} is at the new one. Returns {@code false} when the camera is not started. A larger frame
     * needs a larger {@code jpeg} buffer.
     */
    boolean setFrameSize(FrameSize frameSize);
}
