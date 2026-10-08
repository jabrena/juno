package io.github.jabrena.juno.api.led;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;

/**
 * Scrolls "Juno, Java for Arduino ONE R4" across the built-in LED matrix from right to
 * left, one pixel column per tick, using {@link LedCanvas#drawText}, which unrolls the literal into
 * one {@code drawChar} per character at compile time (this API deliberately requires a compile-time
 * literal even though Juno supports a small runtime {@code String} subset). {@code LedCanvas.setPixel} already
 * clips anything outside the frame, so characters simply appear at the right edge and disappear
 * off the left edge as the offset shrinks.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class LedMatrixScrollingText {
    private static final String MESSAGE = "Juno, Java for Arduino ONE R4";
    // MESSAGE.length() is not a Java compile-time constant, so keep this synchronized with MESSAGE.
    private static final int CHAR_COUNT = 29;
    private static final int CHAR_SPACING = 6;
    private static final int MESSAGE_WIDTH = CHAR_COUNT * CHAR_SPACING;
    private static final int START_OFFSET = 12;
    private static final int END_OFFSET = -MESSAGE_WIDTH;

    public static void main(String[] args) {
        LedMatrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];

        while (true) {
            int offset = START_OFFSET;
            while (offset >= END_OFFSET) {
                LedCanvas.clear(frame);
                LedCanvas.drawText(frame, MESSAGE, offset, 0);
                LedCanvas.show(frame);
                Delay.millis(80);
                LedMatrix.clear();
                offset = offset - 1;
            }
        }
    }
}
