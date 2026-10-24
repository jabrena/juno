package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.util.Delay;

/**
 * Counts from 1 to 10 on the built-in LED matrix using the shared 5x7 font in
 * {@code LedCanvas}/{@code LedMatrixFont}. 1-9 are drawn as a single centered digit; 10 is
 * drawn as two digits side by side, since 5 + 1 gap + 5 = 11 fits within the 12-column matrix.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class})
@SuppressWarnings("java:S2189")
public final class LedMatrixCountUp {
    public static void main(String[] args) {
        LedMatrix matrix = ArduinoOne.getLedMatrix();
        matrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];

        while (true) {
            int number = 1;
            while (number <= 10) {
                LedCanvas.clear(frame);
                if (number < 10) {
                    LedCanvas.drawDigit(frame, number, 4, 0);
                } else {
                    LedCanvas.drawDigit(frame, 1, 0, 0);
                    LedCanvas.drawDigit(frame, 0, 6, 0);
                }

                LedCanvas.show(frame);
                Delay.millis(700);
                matrix.clear();
                Delay.millis(120);
                number = number + 1;
            }
        }
    }
}
