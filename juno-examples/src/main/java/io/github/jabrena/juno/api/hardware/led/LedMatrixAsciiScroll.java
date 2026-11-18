package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

/**
 * Cycles the full printable-ASCII font in {@code AsciiFont} one character at a time on the built-in LED
 * matrix with {@link Display#print(char)}: digits 0-9, then uppercase A-Z, then lowercase a-z, then the
 * punctuation and symbol characters, in that order.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class LedMatrixAsciiScroll {
    private static final Display DISPLAY = MCU.getDisplay();

    public static void main(String[] args) {
        DISPLAY.setTextColor(Display.WHITE, Display.BLACK);
        while (true) {
            for (int code = '0'; code <= '9'; code++) {
                showChar(code);
            }
            for (int code = 'A'; code <= 'Z'; code++) {
                showChar(code);
            }
            for (int code = 'a'; code <= 'z'; code++) {
                showChar(code);
            }
            for (int code = '!'; code <= '/'; code++) {
                showChar(code);
            }
            for (int code = ':'; code <= '@'; code++) {
                showChar(code);
            }
            for (int code = '['; code <= '`'; code++) {
                showChar(code);
            }
            for (int code = '{'; code <= '~'; code++) {
                showChar(code);
            }
        }
    }

    private static void showChar(int asciiCode) {
        DISPLAY.fillScreen(Display.BLACK);
        DISPLAY.setCursor(4, 0);
        DISPLAY.print((char) asciiCode);
        DISPLAY.flush();
        Delay.millis(450);
        DISPLAY.fillScreen(Display.BLACK);
        DISPLAY.flush();
        Delay.millis(80);
    }
}
