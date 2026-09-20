package io.github.jabrena.juno.api.led;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;

/**
 * Scrolls "Juno, Java for Arduino ONE R4" across the UNO R4 WiFi's 12x8 LED matrix from right to
 * left, one pixel column per tick, using {@link LedCanvas#drawText}, which unrolls the literal into
 * one {@code drawChar} per character at compile time (Juno still has no runtime {@code String} — no
 * heap, so the message can only ever be a compile-time literal). {@code LedCanvas.setPixel} already
 * clips anything outside the 12x8 frame, so characters simply appear at the right edge and disappear
 * off the left edge as the offset shrinks.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class LedMatrixScrollingText {
    private static final String MESSAGE = "Juno, Java for Arduino ONE R4";
    // MESSAGE.length(): javac can't fold a String method call to a constant, and Juno has no runtime
    // String#length() either, so this stays a manually-kept-in-sync literal.
    private static final int CHAR_COUNT = 29;
    private static final int CHAR_SPACING = 6;
    private static final int MESSAGE_WIDTH = CHAR_COUNT * CHAR_SPACING;
    private static final int START_OFFSET = 12;
    private static final int END_OFFSET = -MESSAGE_WIDTH;

    public static void main(String[] args) {
        LedMatrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];

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
