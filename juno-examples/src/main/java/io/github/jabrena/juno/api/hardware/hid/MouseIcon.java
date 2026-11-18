package io.github.jabrena.juno.api.hardware.hid;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;

/**
 * Draws the mouse-face icon used by {@link RatonLoco} on the UNO R4 WiFi's built-in 12x8 LED matrix. The icon consists
 * of two solid ears, an outlined head, two eyes, and a nose. Its pixels are positioned using the matrix coordinate
 * system, whose origin is the top-left corner.
 *
 * <p>This is a stateless utility class. {@link #draw()} clears the display, builds the icon from rectangular strips
 * and individual pixels, and sends the completed frame to the matrix.
 */
public final class MouseIcon {
    private static final Display DISPLAY = MCU.getDisplay();

    /** Prevents instantiation; the icon is rendered through {@link #draw()}. */
    private MouseIcon() {
    }

    /**
     * Draws the mouse face on the LED matrix. Because the display starts with every pixel off, this replaces any image
     * currently displayed on the matrix rather than drawing over it.
     */
    public static void draw() {
        DISPLAY.fillScreen(Display.BLACK);
        DISPLAY.fillRect(1, 0, 2, 2, Display.WHITE); // left ear
        DISPLAY.fillRect(9, 0, 2, 2, Display.WHITE); // right ear
        DISPLAY.fillRect(2, 2, 8, 1, Display.WHITE); // head top edge
        DISPLAY.fillRect(2, 3, 1, 2, Display.WHITE); // left side of head
        DISPLAY.fillRect(9, 3, 1, 2, Display.WHITE); // right side of head
        DISPLAY.drawPixel(4, 3, Display.WHITE);      // left eye
        DISPLAY.drawPixel(7, 3, Display.WHITE);      // right eye
        DISPLAY.fillRect(2, 5, 8, 1, Display.WHITE); // chin
        DISPLAY.fillRect(5, 7, 2, 1, Display.WHITE); // nose
        DISPLAY.flush();
    }
}
