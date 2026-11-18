package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

/**
 * Counts a one-decimal-digit reading from 0.0 up to 9.9 on the built-in LED matrix. The whole digit sits at column 0 and
 * the fraction digit at column 7, with a one-pixel decimal point between them at column 6, so "w.f" fits the 12 columns.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class LedMatrixDecimalCountUp {
    private static final Display DISPLAY = MCU.getDisplay();
    private static final int POINT_X = 6;
    private static final int POINT_Y = 6;

    public static void main(String[] args) {
        DISPLAY.setTextColor(Display.WHITE, Display.BLACK);
        while (true) {
            int wholeDigit = 0;
            while (wholeDigit <= 9) {
                int fractionDigit = 0;
                while (fractionDigit <= 9) {
                    DISPLAY.fillScreen(Display.BLACK);
                    DISPLAY.setCursor(0, 0);
                    DISPLAY.print((char) ('0' + wholeDigit));
                    DISPLAY.drawPixel(POINT_X, POINT_Y, Display.WHITE);
                    DISPLAY.setCursor(7, 0);
                    DISPLAY.print((char) ('0' + fractionDigit));
                    DISPLAY.flush();
                    Delay.millis(150);
                    DISPLAY.fillScreen(Display.BLACK);
                    DISPLAY.flush();
                    fractionDigit = fractionDigit + 1;
                }
                wholeDigit = wholeDigit + 1;
            }
        }
    }
}
