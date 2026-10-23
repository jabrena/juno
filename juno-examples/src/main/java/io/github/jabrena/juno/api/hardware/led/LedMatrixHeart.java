package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.util.Delay;

@Board(ArduinoOneR4WiFiBoard.class)
public final class LedMatrixHeart {
    // The UNO R4 WiFi's built-in 12x8 LED matrix; each frame is 96 pixels packed MSB-first,
    // left-to-right/top-to-bottom, into three 32-bit words.
    private static final int HEART_WORD_0 = 0x3184a444;
    private static final int HEART_WORD_1 = 0x44042081;
    private static final int HEART_WORD_2 = 0x100a0040;

    public static void main(String[] args) {
        LedMatrix matrix = ArduinoOne.getLedMatrix();
        matrix.begin();
        while (true) {
            matrix.loadFrame(HEART_WORD_0, HEART_WORD_1, HEART_WORD_2);
            Delay.millis(500);
            matrix.clear();
            Delay.millis(500);
        }
    }
}
