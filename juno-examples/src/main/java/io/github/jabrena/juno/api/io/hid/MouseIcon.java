package io.github.jabrena.juno.api.io.hid;

import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * Draws the mouse-face icon used by {@link RatonLoco} on the UNO R4 WiFi's built-in 12x8 LED
 * matrix. The icon consists of two solid ears, an outlined head, two eyes, and a nose. Its pixels
 * are positioned using the matrix coordinate system, whose origin is the top-left corner.
 *
 * <p>This is a stateless utility class. {@link #draw()} creates a blank frame, builds the icon from
 * {@link LedCanvas#fillRect(boolean[][], int, int, int, int) rectangular strips} and
 * {@link LedCanvas#setPixel(boolean[][], int, int) individual pixels}, and immediately sends the
 * completed frame to the matrix. Call {@link LedMatrix#begin()} before drawing the icon.
 */
public final class MouseIcon {

    /** Prevents instantiation; the icon is rendered through {@link #draw()}. */
    private MouseIcon() {
    }

    /**
     * Creates a new 12x8 frame containing the mouse face and displays it on the LED matrix.
     * Because the frame starts with every pixel off, this replaces any image currently displayed
     * on the matrix rather than drawing over it.
     *
     * <p>The LED matrix must already have been initialized with {@link LedMatrix#begin()}.
     */
    public static void draw() {
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
        LedCanvas.fillRect(frame, 1, 0, 2, 2); // left ear
        LedCanvas.fillRect(frame, 9, 0, 2, 2); // right ear
        LedCanvas.fillRect(frame, 2, 2, 8, 1); // head top edge
        LedCanvas.fillRect(frame, 2, 3, 1, 2); // left side of head
        LedCanvas.fillRect(frame, 9, 3, 1, 2); // right side of head
        LedCanvas.setPixel(frame, 4, 3);       // left eye
        LedCanvas.setPixel(frame, 7, 3);       // right eye
        LedCanvas.fillRect(frame, 2, 5, 8, 1); // chin
        LedCanvas.fillRect(frame, 5, 7, 2, 1); // nose
        LedCanvas.show(frame);
    }
}
