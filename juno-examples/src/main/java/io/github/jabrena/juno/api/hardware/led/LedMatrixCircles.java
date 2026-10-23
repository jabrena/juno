package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.util.Delay;

/**
 * Cycles a filled circle and an outlined circle, centered on the built-in LED matrix,
 * using {@code LedCanvas}.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class})
public final class LedMatrixCircles {
    private static final int CENTER_X = 5;
    private static final int CENTER_Y = 3;
    private static final int RADIUS = 3;

    public static void main(String[] args) {
        LedMatrix matrix = Arduino.getLedMatrix();
        matrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];

        while (true) {
            LedCanvas.clear(frame);
            LedCanvas.fillCircle(frame, CENTER_X, CENTER_Y, RADIUS);
            LedCanvas.show(frame);
            Delay.millis(800);
            matrix.clear();

            LedCanvas.clear(frame);
            LedCanvas.drawCircle(frame, CENTER_X, CENTER_Y, RADIUS);
            LedCanvas.show(frame);
            Delay.millis(800);
            matrix.clear();
        }
    }
}
