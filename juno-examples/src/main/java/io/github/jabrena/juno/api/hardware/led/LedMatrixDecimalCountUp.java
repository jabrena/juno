package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.util.Delay;

/**
 * Counts a one-decimal-digit reading from 0.0 up to 9.9 on the built-in LED matrix,
 * using the compact 3x5 font in {@code LedCanvas}/{@code LedMatrixFontSmall}. Unlike the
 * 5x7 font used by {@code LedMatrixCountUp}, the 3x5 digits leave room for a decimal point, so
 * "wholeDigit.fractionDigit" fits as a single 9-column group, centered with 1 column to spare on
 * each side.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class})
public final class LedMatrixDecimalCountUp {
    public static void main(String[] args) {
        LedMatrix matrix = ArduinoOne.getLedMatrix();
        matrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];

        while (true) {
            int wholeDigit = 0;
            while (wholeDigit <= 9) {
                int fractionDigit = 0;
                while (fractionDigit <= 9) {
                    LedCanvas.clear(frame);
                    LedCanvas.drawDecimal(frame, wholeDigit, fractionDigit, 1, 1);

                    LedCanvas.show(frame);
                    Delay.millis(150);
                    matrix.clear();
                    fractionDigit = fractionDigit + 1;
                }
                wholeDigit = wholeDigit + 1;
            }
        }
    }
}
