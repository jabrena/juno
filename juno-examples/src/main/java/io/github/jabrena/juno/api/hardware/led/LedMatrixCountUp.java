package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

/**
 * Counts from 1 to 10 on the built-in LED matrix with the 5x7 font of {@link Display#print(char)}. 1-9 are drawn as a
 * single digit at column 4; 10 is drawn as two digits side by side, since 5 + 1 gap + 5 = 11 fits within the 12-column
 * matrix.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class LedMatrixCountUp {
    private static final Display DISPLAY = MCU.getDisplay();

    public static void main(String[] args) {
        DISPLAY.setTextColor(Display.WHITE, Display.BLACK);
        while (true) {
            int number = 1;
            while (number <= 10) {
                DISPLAY.fillScreen(Display.BLACK);
                if (number < 10) {
                    DISPLAY.setCursor(4, 0);
                    DISPLAY.print((char) ('0' + number));
                } else {
                    DISPLAY.setCursor(0, 0);
                    DISPLAY.print('1');
                    DISPLAY.setCursor(6, 0);
                    DISPLAY.print('0');
                }
                DISPLAY.flush();
                Delay.millis(700);
                DISPLAY.fillScreen(Display.BLACK);
                DISPLAY.flush();
                Delay.millis(120);
                number = number + 1;
            }
        }
    }
}
