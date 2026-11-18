package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

/** Cycles a filled circle and an outlined circle, centered on the built-in LED matrix. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class LedMatrixCircles {
    private static final Display DISPLAY = MCU.getDisplay();
    private static final int CENTER_X = 5;
    private static final int CENTER_Y = 3;
    private static final int RADIUS = 3;

    public static void main(String[] args) {
        while (true) {
            DISPLAY.fillScreen(Display.BLACK);
            DISPLAY.fillCircle(CENTER_X, CENTER_Y, RADIUS, Display.WHITE);
            DISPLAY.flush();
            Delay.millis(800);
            blank();

            DISPLAY.fillScreen(Display.BLACK);
            DISPLAY.drawCircle(CENTER_X, CENTER_Y, RADIUS, Display.WHITE);
            DISPLAY.flush();
            Delay.millis(800);
            blank();
        }
    }

    private static void blank() {
        DISPLAY.fillScreen(Display.BLACK);
        DISPLAY.flush();
    }
}
