package io.github.jabrena.juno.util.image;

/** An image that draws itself on the TFT, centred over the current viewport. */
public interface Drawable {
    /** Returns the image width in pixels. */
    int getWidth();

    /** Returns the image height in pixels. */
    int getHeight();

    void draw();

    /** Draws the image's pixels that intersect the inclusive screen-space clip rectangle. */
    default void draw(int clipLeft, int clipTop, int clipRight, int clipBottom) {
        draw();
    }
}
