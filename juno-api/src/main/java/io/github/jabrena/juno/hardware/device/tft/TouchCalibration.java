package io.github.jabrena.juno.hardware.device.tft;

/**
 * Turns an XPT2046's raw X and Y readings into panel coordinates: which raw axis runs along the screen's width, and
 * the raw readings at the screen's left, right, top and bottom edges. A larger "left" than "right" reading mirrors
 * that axis.
 */
final class TouchCalibration {
    private boolean swapAxes;
    private int left;
    private int right;
    private int top;
    private int bottom;

    void set(boolean swap, int rawLeft, int rawRight, int rawTop, int rawBottom) {
        swapAxes = swap;
        left = rawLeft;
        right = rawRight;
        top = rawTop;
        bottom = rawBottom;
    }

    void edges(int rawLeft, int rawRight, int rawTop, int rawBottom) {
        set(swapAxes, rawLeft, rawRight, rawTop, rawBottom);
    }

    /** The panel column, {@code 0} to {@code width - 1}, of a touch with these raw readings. */
    int column(int rawX, int rawY, int width) {
        return clamp(scale(swapAxes ? rawY : rawX, left, right, width), width - 1);
    }

    /** The panel row, {@code 0} to {@code height - 1}, of a touch with these raw readings. */
    int row(int rawX, int rawY, int height) {
        return clamp(scale(swapAxes ? rawX : rawY, top, bottom, height), height - 1);
    }

    private static int scale(int raw, int rawMin, int rawMax, int span) {
        if (rawMax == rawMin) {
            return 0;
        }
        return (raw - rawMin) * span / (rawMax - rawMin);
    }

    static int clamp(int value, int max) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, max);
    }
}
