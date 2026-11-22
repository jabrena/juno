package io.github.jabrena.juno.hardware.device.camera;

/**
 * The frame sizes {@link Camera#setFrameSize(FrameSize)} switches the sensor between, smallest first. A larger frame
 * needs a larger JPEG buffer: {@link Camera#capture(byte[])} reports a frame that does not fit as a failure.
 */
public enum FrameSize {
    /** 160 x 120: the size {@link Camera#begin()} starts at, small enough to stream from a few kilobytes of buffer. */
    QQVGA(160, 120),
    /** 320 x 240. */
    QVGA(320, 240),
    /** 640 x 480: a JPEG takes tens of kilobytes. */
    VGA(640, 480),
    /** 800 x 600, the largest size. */
    SVGA(800, 600);

    private final int width;
    private final int height;

    FrameSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** The frame's width in pixels. */
    public int width() {
        return width;
    }

    /** The frame's height in pixels. */
    public int height() {
        return height;
    }
}
