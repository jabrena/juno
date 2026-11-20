package io.github.jabrena.juno.api.hardware.hid;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;

/**
 * Draws the mouse-face icon used by {@link RatonLoco} on the board's display: the UNO R4 WiFi's built-in 12x8 LED
 * matrix, or the M5Stack Cardputer's screen. The icon consists of two solid ears, an outlined head, two eyes, and a
 * nose, laid out on a 12x8 grid whose origin is the top-left corner.
 *
 * <p>Each grid cell is drawn as a square block, as large as the display allows, and the face is centered: one pixel
 * per cell on the matrix, a large face on the Cardputer's screen. This is a stateless utility class; {@link #draw()}
 * clears the display, draws the icon and sends the completed frame to it.
 */
public final class MouseIcon {
    private static final int COLUMNS = 12;
    private static final int ROWS = 8;

    private static int scale;
    private static int left;
    private static int top;

    /**
     * Draws the mouse face on the board's display. Because the display is cleared first, this replaces any image
     * currently displayed rather than drawing over it.
     *
     * <p>The display is asked for here, at call time, rather than kept in a static field: a static initializer that
     * calls {@link MCU#getDisplay()} can run before the display driver's own and capture no display.
     */
    public static void draw() {
        Display display = MCU.getDisplay();
        scale = Math.min(display.width() / COLUMNS, display.height() / ROWS);
        left = (display.width() - COLUMNS * scale) / 2;
        top = (display.height() - ROWS * scale) / 2;

        display.fillScreen(Display.BLACK);
        cells(display, 1, 0, 2, 2); // left ear
        cells(display, 9, 0, 2, 2); // right ear
        cells(display, 2, 2, 8, 1); // head top edge
        cells(display, 2, 3, 1, 2); // left side of head
        cells(display, 9, 3, 1, 2); // right side of head
        cells(display, 4, 3, 1, 1); // left eye
        cells(display, 7, 3, 1, 1); // right eye
        cells(display, 2, 5, 8, 1); // chin
        cells(display, 5, 7, 2, 1); // nose
        display.flush();
    }

    /** Lights the grid cells from ({@code x}, {@code y}), {@code width} by {@code height} cells, scaled to the display. */
    private static void cells(Display display, int x, int y, int width, int height) {
        display.fillRect(left + x * scale, top + y * scale, width * scale, height * scale, Display.WHITE);
    }
}
