package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

/**
 * Scrolls "Juno, Java for Arduino ONE R4" across the built-in LED matrix from right to left, one pixel column per tick.
 * {@link Display#print(String)} clips anything outside the matrix, so characters appear at the right edge and disappear
 * off the left edge as the offset shrinks.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class LedMatrixScrollingText {
    private static final Display DISPLAY = MCU.getDisplay();
    private static final String MESSAGE = "Juno, Java for Arduino ONE R4";
    // MESSAGE.length() is not a Java compile-time constant, so keep this synchronized with MESSAGE.
    private static final int CHAR_COUNT = 29;
    private static final int CHAR_SPACING = 6;
    private static final int MESSAGE_WIDTH = CHAR_COUNT * CHAR_SPACING;
    private static final int START_OFFSET = 12;
    private static final int END_OFFSET = -MESSAGE_WIDTH;

    public static void main(String[] args) {
        DISPLAY.setTextColor(Display.WHITE, Display.BLACK);
        while (true) {
            int offset = START_OFFSET;
            while (offset >= END_OFFSET) {
                DISPLAY.fillScreen(Display.BLACK);
                DISPLAY.setCursor(offset, 0);
                DISPLAY.print(MESSAGE);
                DISPLAY.flush();
                Delay.millis(80);
                DISPLAY.fillScreen(Display.BLACK);
                DISPLAY.flush();
                offset = offset - 1;
            }
        }
    }
}
