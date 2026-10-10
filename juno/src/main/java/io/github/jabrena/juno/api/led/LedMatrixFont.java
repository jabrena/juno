package io.github.jabrena.juno.api.led;

/**
 * A classic 5x7 dot-matrix font for digits and uppercase letters, sized to fit the UNO R4 WiFi's
 * 12x8 LED matrix (two glyphs fit side by side: 5 + 1 gap + 5 = 11 of the 12 columns).
 *
 * <p>The shapes are {@link LedMatrixFontAscii}'s for the codes '0'-'9' and 'A'-'Z': for a given row
 * (0 = top .. 6 = bottom), a 5-bit mask of its lit columns (bit 4 = leftmost column), read from its
 * constant table in flash.
 */
public final class LedMatrixFont {
    public static final int GLYPH_WIDTH = 5;
    public static final int GLYPH_HEIGHT = 7;

    private LedMatrixFont() {
    }

    /** digit: 0-9. col: 0 (left) to {@link #GLYPH_WIDTH} - 1. row: 0 (top) to {@link #GLYPH_HEIGHT} - 1. */
    public static boolean digitPixel(int digit, int col, int row) {
        return LedMatrixFontAscii.charPixel('0' + digit, col, row);
    }

    /** letter: 0 = 'A' .. 25 = 'Z'. col/row as {@link #digitPixel}. */
    public static boolean letterPixel(int letter, int col, int row) {
        return LedMatrixFontAscii.charPixel('A' + letter, col, row);
    }
}
