package io.github.jabrena.juno.api;

/**
 * Pixel-addressing helpers for the UNO R4 WiFi's built-in 12x8 LED matrix. A frame is 96 pixels
 * packed MSB-first, left-to-right/top-to-bottom, into three 32-bit words, exactly as
 * {@link LedMatrix#loadFrame(int, int, int)} expects.
 */
public final class LedCanvas {
    public static final int WIDTH = 12;
    public static final int HEIGHT = 8;

    private LedCanvas() {
    }

    public static boolean inBounds(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    public static int packPos(int x, int y) {
        return y * WIDTH + x;
    }

    /** Lights pixel (x, y) in {@code word} when {@code word} is frame word {@code wordIndex}. */
    public static int setPixel(int word, int wordIndex, int x, int y) {
        if (!inBounds(x, y)) {
            return word;
        }
        int index = packPos(x, y);
        int targetWord = index / 32;
        if (targetWord != wordIndex) {
            return word;
        }
        int bitFromTop = index - targetWord * 32;
        int shift = 31 - bitFromTop;
        return word | (1 << shift);
    }
}
