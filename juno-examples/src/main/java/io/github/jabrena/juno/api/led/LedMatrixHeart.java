package io.github.jabrena.juno.api.led;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;

@Board(ArduinoUnoR4WiFi.class)
public final class LedMatrixHeart {
    // The UNO R4 WiFi's built-in 12x8 LED matrix; each frame is 96 pixels packed MSB-first,
    // left-to-right/top-to-bottom, into three 32-bit words.
    private static final int HEART_WORD_0 = 0x3184a444;
    private static final int HEART_WORD_1 = 0x44042081;
    private static final int HEART_WORD_2 = 0x100a0040;

    public static void main(String[] args) {
        LedMatrix.begin();
        while (true) {
            LedMatrix.loadFrame(HEART_WORD_0, HEART_WORD_1, HEART_WORD_2);
            Delay.millis(500);
            LedMatrix.clear();
            Delay.millis(500);
        }
    }
}
