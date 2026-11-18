package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.util.Delay;

@Board(ArduinoUnoR4WiFiBoard.class)
public final class LedMatrixHeart {
    // The UNO R4 WiFi's built-in 12x8 LED matrix; each frame is 96 pixels packed MSB-first,
    // left-to-right/top-to-bottom, into three 32-bit words.
    private static final int HEART_WORD_0 = 0x3184a444;
    private static final int HEART_WORD_1 = 0x44042081;
    private static final int HEART_WORD_2 = 0x100a0040;
    private static final int COLUMNS = 12;

    public static void main(String[] args) {
        boolean[][] heart = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
        boolean[][] blank = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
        unpack(heart, 0, HEART_WORD_0);
        unpack(heart, 1, HEART_WORD_1);
        unpack(heart, 2, HEART_WORD_2);
        while (true) {
            LedCanvas.show(heart);
            Delay.millis(500);
            LedCanvas.show(blank);
            Delay.millis(500);
        }
    }

    /** Copies the 32 pixels of packed word {@code wordIndex} into {@code frame}, most significant bit first. */
    private static void unpack(boolean[][] frame, int wordIndex, int word) {
        for (int bit = 0; bit < 32; bit++) {
            int pixel = wordIndex * 32 + bit;
            if (((word >> (31 - bit)) & 1) != 0) {
                frame[pixel / COLUMNS][pixel % COLUMNS] = true;
            }
        }
    }
}
