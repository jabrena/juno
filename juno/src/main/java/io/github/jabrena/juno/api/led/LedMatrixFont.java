package io.github.jabrena.juno.api.led;

/**
 * A classic 5x7 dot-matrix font for digits and uppercase letters, sized to fit the UNO R4 WiFi's
 * 12x8 LED matrix (two glyphs fit side by side: 5 + 1 gap + 5 = 11 of the 12 columns).
 *
 * <p>Juno v0.1 has no arrays, so each glyph is encoded as a small function returning, for a given
 * row (0 = top .. 6 = bottom), a 5-bit mask of its lit columns (bit 4 = leftmost column). Only the
 * glyphs a program actually calls are linked into the generated sketch, so drawing digits alone
 * never pulls the letter table into flash.
 */
public final class LedMatrixFont {
    public static final int GLYPH_WIDTH = 5;
    public static final int GLYPH_HEIGHT = 7;

    private LedMatrixFont() {
    }

    /** digit: 0-9. col: 0 (left) to {@link #GLYPH_WIDTH} - 1. row: 0 (top) to {@link #GLYPH_HEIGHT} - 1. */
    public static boolean digitPixel(int digit, int col, int row) {
        return isSet(digitRowBits(digit, row), col);
    }

    /** letter: 0 = 'A' .. 25 = 'Z'. col/row as {@link #digitPixel}. */
    public static boolean letterPixel(int letter, int col, int row) {
        return isSet(letterRowBits(letter, row), col);
    }

    private static boolean isSet(int rowBits, int col) {
        return ((rowBits >> (GLYPH_WIDTH - 1 - col)) & 1) != 0;
    }

    /**
     * Row-bit tables live in per-range shard classes ({@link LedMatrixDigits0to4},
     * {@link LedMatrixDigits5to9}, {@link LedMatrixLettersAtoG}, {@link LedMatrixLettersHtoN},
     * {@link LedMatrixLettersOtoT}, {@link LedMatrixLettersUtoZ}) so only the glyphs a program
     * actually reaches are linked in, and so no one class's cyclomatic complexity grows with the
     * whole alphabet.
     */
    private static int digitRowBits(int digit, int row) {
        if (digit < 5) {
            return LedMatrixDigits0to4.rowBits(digit, row);
        }
        return LedMatrixDigits5to9.rowBits(digit - 5, row);
    }

    private static int letterRowBits(int letter, int row) {
        if (letter < 7) {
            return LedMatrixLettersAtoG.rowBits(letter, row);
        }
        if (letter < 14) {
            return LedMatrixLettersHtoN.rowBits(letter - 7, row);
        }
        if (letter < 20) {
            return LedMatrixLettersOtoT.rowBits(letter - 14, row);
        }
        return LedMatrixLettersUtoZ.rowBits(letter - 20, row);
    }
}
