package io.github.jabrena.juno.api.hardware.display;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

/**
 * Counts from 0 to 9 on the built-in LED matrix through {@link MCU#getDisplay()}, the same call a Cardputer program
 * uses for its panel. The matrix is one color and 12 or 13 pixels wide, so the digit is centered from
 * {@link Display#width()} and the frame is sent with {@link Display#flush()}.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class LedMatrixDisplayCountUp {
    private static final Display DISPLAY = MCU.getDisplay();
    private static final int GLYPH_WIDTH = 5;

    public static void main(String[] args) {
        DISPLAY.setTextColor(Display.WHITE, Display.BLACK);
        while (true) {
            for (int digit = 0; digit < 10; digit++) {
                DISPLAY.fillScreen(Display.BLACK);
                DISPLAY.setCursor((DISPLAY.width() - GLYPH_WIDTH) / 2, 0);
                DISPLAY.print((char) ('0' + digit));
                DISPLAY.flush();
                Delay.millis(500);
            }
        }
    }
}
