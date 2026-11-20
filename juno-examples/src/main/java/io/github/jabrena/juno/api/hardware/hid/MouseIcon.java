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

    /**
     * Draws the mouse face on the LED matrix. Because the display starts with every pixel off, this replaces any image
     * currently displayed on the matrix rather than drawing over it.
     *
     * <p>The display is asked for here, at call time, rather than kept in a static field: a static initializer that
     * calls {@link MCU#getDisplay()} can run before the matrix driver's own and capture no display.
     */
    public static void draw() {
        Display display = MCU.getDisplay();
        display.fillScreen(Display.BLACK);
        display.fillRect(1, 0, 2, 2, Display.WHITE); // left ear
        display.fillRect(9, 0, 2, 2, Display.WHITE); // right ear
        display.fillRect(2, 2, 8, 1, Display.WHITE); // head top edge
        display.fillRect(2, 3, 1, 2, Display.WHITE); // left side of head
        display.fillRect(9, 3, 1, 2, Display.WHITE); // right side of head
        display.drawPixel(4, 3, Display.WHITE);      // left eye
        display.drawPixel(7, 3, Display.WHITE);      // right eye
        display.fillRect(2, 5, 8, 1, Display.WHITE); // chin
        display.fillRect(5, 7, 2, 1, Display.WHITE); // nose
        display.flush();
    }
}
