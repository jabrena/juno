package io.github.jabrena.juno.api.hardware.display.led;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

@Board(ArduinoUnoR4WiFiBoard.class)
public final class LedMatrixHeart {
    private static final Display DISPLAY = MCU.getDisplay();
    // The UNO R4 WiFi's built-in 12x8 LED matrix; each frame is 96 pixels packed MSB-first,
    // left-to-right/top-to-bottom, into three 32-bit words.
    private static final int[] HEART = {0x3184a444, 0x44042081, 0x100a0040};
    private static final int COLUMNS = 12;
    private static final int PIXELS = 96;

    public static void main(String[] args) {
        while (true) {
            drawHeart();
            DISPLAY.flush();
            Delay.millis(500);
            DISPLAY.fillScreen(Display.BLACK);
            DISPLAY.flush();
            Delay.millis(500);
        }
    }

    /** Draws every lit pixel of {@link #HEART}, most significant bit first. */
    private static void drawHeart() {
        for (int pixel = 0; pixel < PIXELS; pixel++) {
            if (((HEART[pixel >> 5] >> (31 - (pixel & 31))) & 1) != 0) {
                DISPLAY.drawPixel(pixel % COLUMNS, pixel / COLUMNS, Display.WHITE);
            }
        }
    }
}
