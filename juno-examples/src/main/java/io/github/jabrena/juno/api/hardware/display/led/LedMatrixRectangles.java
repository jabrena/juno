package io.github.jabrena.juno.api.hardware.display.led;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

/**
 * Cycles a filled square, an outlined square, a filled rectangle, and an outlined rectangle on the built-in LED matrix.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class LedMatrixRectangles {
    private static final Display DISPLAY = MCU.getDisplay();

    public static void main(String[] args) {
        while (true) {
            DISPLAY.fillScreen(Display.BLACK);
            DISPLAY.fillRect(3, 1, 6, 6, Display.WHITE);
            show(800);

            DISPLAY.fillScreen(Display.BLACK);
            DISPLAY.drawRect(3, 1, 6, 6, Display.WHITE);
            show(800);

            DISPLAY.fillScreen(Display.BLACK);
            DISPLAY.fillRect(1, 2, 10, 4, Display.WHITE);
            show(800);

            DISPLAY.fillScreen(Display.BLACK);
            DISPLAY.drawRect(1, 2, 10, 4, Display.WHITE);
            show(800);
        }
    }

    /** Sends the drawn frame to the matrix, holds it for {@code millis}, then turns the matrix off. */
    private static void show(int millis) {
        DISPLAY.flush();
        Delay.millis(millis);
        DISPLAY.fillScreen(Display.BLACK);
        DISPLAY.flush();
    }
}
