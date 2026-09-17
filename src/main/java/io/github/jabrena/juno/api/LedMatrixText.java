package io.github.jabrena.juno.api;

/**
 * Draws {@link LedMatrixFont} glyphs into a {@link LedMatrix#loadFrame(int, int, int)} word.
 * Callers build a frame by OR-ing one or more glyphs (and any other pixels) into the same
 * {@code word0}/{@code word1}/{@code word2} triple, then pass all three to {@code loadFrame}.
 */
public final class LedMatrixText {
    private LedMatrixText() {
    }

    /** Draws digit (0-9) into {@code word}, which must be frame word {@code wordIndex} (0-2). */
    public static int drawDigit(int word, int wordIndex, int digit, int originX, int originY) {
        int result = word;
        for (int row = 0; row < LedMatrixFont.GLYPH_HEIGHT; row++) {
            for (int col = 0; col < LedMatrixFont.GLYPH_WIDTH; col++) {
                if (LedMatrixFont.digitPixel(digit, col, row)) {
                    result = LedCanvas.setPixel(result, wordIndex, originX + col, originY + row);
                }
            }
        }
        return result;
    }

    /** Draws letter (0 = 'A' .. 25 = 'Z') into {@code word}, frame word {@code wordIndex} (0-2). */
    public static int drawLetter(int word, int wordIndex, int letter, int originX, int originY) {
        int result = word;
        for (int row = 0; row < LedMatrixFont.GLYPH_HEIGHT; row++) {
            for (int col = 0; col < LedMatrixFont.GLYPH_WIDTH; col++) {
                if (LedMatrixFont.letterPixel(letter, col, row)) {
                    result = LedCanvas.setPixel(result, wordIndex, originX + col, originY + row);
                }
            }
        }
        return result;
    }
}
