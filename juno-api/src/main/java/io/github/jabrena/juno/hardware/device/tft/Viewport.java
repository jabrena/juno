package io.github.jabrena.juno.hardware.device.tft;

/**
 * Maps a program's logical drawing size onto a larger panel: a uniform scale of {@code numerator / denominator}, so
 * shapes keep their proportions, and an origin that centers the scaled area. A logical pixel covers the physical
 * pixels from its own position up to, not including, the next one's, so adjacent rectangles stay adjacent. The
 * identity viewport draws at the panel's own resolution.
 */
final class Viewport {
    private int numerator = 1;
    private int denominator = 1;
    private int originX;
    private int originY;

    /** Draws at the panel's own resolution again. */
    void identity() {
        numerator = 1;
        denominator = 1;
        originX = 0;
        originY = 0;
    }

    /** Scales {@code width} x {@code height} up, as far as both fit, onto the panel's size, centered. */
    void fit(int panelWidth, int panelHeight, int width, int height) {
        if (panelWidth * height <= panelHeight * width) {
            numerator = panelWidth;
            denominator = width;
        } else {
            numerator = panelHeight;
            denominator = height;
        }
        originX = (panelWidth - width * numerator / denominator) / 2;
        originY = (panelHeight - height * numerator / denominator) / 2;
    }

    boolean isIdentity() {
        return numerator == denominator && originX == 0 && originY == 0;
    }

    /** The panel column where logical column {@code x} starts. */
    int panelX(int x) {
        return originX + x * numerator / denominator;
    }

    /** The panel row where logical row {@code y} starts. */
    int panelY(int y) {
        return originY + y * numerator / denominator;
    }

    /** The logical column that covers panel column {@code x}; negative left of the scaled area. */
    int logicalX(int x) {
        return logical(x - originX);
    }

    /** The logical row that covers panel row {@code y}; negative above the scaled area. */
    int logicalY(int y) {
        return logical(y - originY);
    }

    // The largest logical position whose first panel pixel is at or before this offset.
    private int logical(int offset) {
        if (offset < 0) {
            return -1;
        }
        return (denominator * (offset + 1) + numerator - 1) / numerator - 1;
    }
}
