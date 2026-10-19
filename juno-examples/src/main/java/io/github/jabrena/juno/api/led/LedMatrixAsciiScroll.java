package io.github.jabrena.juno.api.led;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Cycles the full printable-ASCII font in {@code LedMatrixFontAscii} one character at a time on
 * the built-in LED matrix using {@code LedCanvas.drawChar}: digits 0-9, then
 * uppercase A-Z, then lowercase a-z, then the punctuation/symbol characters, in that order.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class LedMatrixAsciiScroll {
    public static void main(String[] args) {
        LedMatrix matrix = MCU.getLedMatrix();
        matrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];

        while (true) {
            for (int code = '0'; code <= '9'; code++) {
                showChar(matrix, frame, code);
            }
            for (int code = 'A'; code <= 'Z'; code++) {
                showChar(matrix, frame, code);
            }
            for (int code = 'a'; code <= 'z'; code++) {
                showChar(matrix, frame, code);
            }
            for (int code = '!'; code <= '/'; code++) {
                showChar(matrix, frame, code);
            }
            for (int code = ':'; code <= '@'; code++) {
                showChar(matrix, frame, code);
            }
            for (int code = '['; code <= '`'; code++) {
                showChar(matrix, frame, code);
            }
            for (int code = '{'; code <= '~'; code++) {
                showChar(matrix, frame, code);
            }
        }
    }

    private static void showChar(LedMatrix matrix, boolean[][] frame, int asciiCode) {
        LedCanvas.clear(frame);
        LedCanvas.drawChar(frame, asciiCode, 4, 0);
        LedCanvas.show(frame);
        Delay.millis(450);
        matrix.clear();
        Delay.millis(80);
    }
}
