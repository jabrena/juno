package io.github.jabrena.juno.api;

/**
 * Draws {@link LedMatrixFontSmall} glyphs into a {@link LedMatrix#loadFrame(int, int, int)} word.
 * This is the smaller counterpart to {@link LedMatrixText}: its 3x5 digits leave room to also draw
 * a decimal point, so a one-decimal-digit reading like "2.5" fits as a single group on the 12x8
 * matrix instead of needing two full 5x7 digits with no room for the point.
 */
public final class LedMatrixSmallText {
    private LedMatrixSmallText() {
    }

    /** Draws digit (0-9) with the small font into {@code word}, frame word {@code wordIndex} (0-2). */
    public static int drawSmallDigit(int word, int wordIndex, int digit, int originX, int originY) {
        int result = word;
        for (int row = 0; row < LedMatrixFontSmall.GLYPH_HEIGHT; row++) {
            for (int col = 0; col < LedMatrixFontSmall.GLYPH_WIDTH; col++) {
                if (LedMatrixFontSmall.digitPixel(digit, col, row)) {
                    result = LedCanvas.setPixel(result, wordIndex, originX + col, originY + row);
                }
            }
        }
        return result;
    }

    /**
     * Draws a one-decimal-digit reading such as "2.5" (wholeDigit = 2, fractionDigit = 5): a small
     * digit, a decimal point, and another small digit, laid out left to right from originX. The
     * group is 9 columns wide (3 + 1 gap + 1 point + 1 gap + 3), leaving 3 of the matrix's 12
     * columns free to center it, e.g. originX = 1.
     */
    public static int drawDecimal(int word, int wordIndex, int wholeDigit, int fractionDigit,
            int originX, int originY) {
        int result = drawSmallDigit(word, wordIndex, wholeDigit, originX, originY);
        int pointX = originX + LedMatrixFontSmall.GLYPH_WIDTH + 1;
        for (int row = 0; row < LedMatrixFontSmall.GLYPH_HEIGHT; row++) {
            if (LedMatrixFontSmall.pointPixel(row)) {
                result = LedCanvas.setPixel(result, wordIndex, pointX, originY + row);
            }
        }
        int fractionX = pointX + 2;
        result = drawSmallDigit(result, wordIndex, fractionDigit, fractionX, originY);
        return result;
    }
}
