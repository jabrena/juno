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
 */
public interface Camera {
    /** 160 x 120: the size {@link #begin()} starts at, small enough to stream from a few kilobytes of buffer. */
    int FRAME_SIZE_QQVGA = 0;
    /** 320 x 240. */
    int FRAME_SIZE_QVGA = 1;
    /** 640 x 480: a JPEG takes tens of kilobytes. */
    int FRAME_SIZE_VGA = 2;
    /** 800 x 600, the largest size. */
    int FRAME_SIZE_SVGA = 3;

    /** Starts the camera at its fixed JPEG frame size and returns whether it initialized. */
    boolean begin();

    /**
     * Waits for the next frame and copies its JPEG bytes into {@code jpeg} from index 0. Returns the frame's length in
     * bytes, or {@code -1} when the camera is not started, no frame arrived, or the frame is larger than {@code jpeg}.
     */
    int capture(byte[] jpeg);

    /**
     * Switches the sensor to one of the {@code FRAME_SIZE_*} sizes and drops the frames already taken at the old size,
     * so the next {@link #capture(byte[])} is at the new one. Returns {@code false} when the camera is not started or
     * the size is unknown. A larger frame needs a larger {@code jpeg} buffer.
     */
    boolean setFrameSize(int frameSize);
}
