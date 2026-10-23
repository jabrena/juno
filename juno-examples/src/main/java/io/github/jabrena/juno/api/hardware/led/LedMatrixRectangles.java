package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.util.Delay;

/**
 * Cycles a filled square, an outlined square, a filled rectangle, and an outlined rectangle on the
 * built-in LED matrix using {@code LedCanvas}.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class})
public final class LedMatrixRectangles {
    public static void main(String[] args) {
        LedMatrix matrix = Arduino.getLedMatrix();
        matrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];

        while (true) {
            LedCanvas.clear(frame);
            LedCanvas.fillRect(frame, 3, 1, 6, 6);
            LedCanvas.show(frame);
            Delay.millis(800);
            matrix.clear();

            LedCanvas.clear(frame);
            LedCanvas.drawRect(frame, 3, 1, 6, 6);
            LedCanvas.show(frame);
            Delay.millis(800);
            matrix.clear();

            LedCanvas.clear(frame);
            LedCanvas.fillRect(frame, 1, 2, 10, 4);
            LedCanvas.show(frame);
            Delay.millis(800);
            matrix.clear();

            LedCanvas.clear(frame);
            LedCanvas.drawRect(frame, 1, 2, 10, 4);
            LedCanvas.show(frame);
            Delay.millis(800);
            matrix.clear();
        }
    }
}
