package io.github.jabrena.juno.api.led;

/**
 * A 5x7 dot-matrix font covering the full printable ASCII range (32 space .. 126 {@code ~}), for
 * printing arbitrary ASCII characters on the UNO R4 WiFi's 12x8 LED matrix one at a time (two
 * glyphs fit side by side: 5 + 1 gap + 5 = 11 of the 12 columns). Digits and uppercase letters
 * reuse {@link LedMatrixFont}'s shapes; this class adds space, punctuation/symbols, and distinct
 * lowercase letter shapes so every printable ASCII code has a glyph.
 *
 * <p>Juno v0.1 has no {@code String}, so there is no "print a string" entry point here: callers
 * draw one {@code char}/ASCII code at a time with {@link #charPixel}, exactly like
 * {@link LedMatrixFont#digitPixel} and {@link LedMatrixFont#letterPixel}.
 *
 * <p>Unlike {@code digitPixel}/{@code letterPixel}, which only link in the specific glyph table a
 * program actually calls, {@link #charPixel} is a single dispatcher spanning the whole printable
 * range: calling it for even one character statically reaches every symbol/lowercase glyph
 * function below, so the whole table is linked into flash. Programs that only ever print digits
 * and/or uppercase letters should keep using {@link LedMatrixFont} directly to stay flash-lean.
 *
 * <p>As with {@link LedMatrixFont}, each glyph is encoded as a small function returning, for a
 * given row (0 = top .. 6 = bottom), a 5-bit mask of its lit columns (bit 4 = leftmost column).
 */
public final class LedMatrixFontAscii {
    public static final int GLYPH_WIDTH = 5;
    public static final int GLYPH_HEIGHT = 7;

    private LedMatrixFontAscii() {
    }

    /** asciiCode: 32 (space) to 126 ('~'). col: 0 (left) to {@link #GLYPH_WIDTH} - 1. row: 0 (top) to {@link #GLYPH_HEIGHT} - 1. */
    public static boolean charPixel(int asciiCode, int col, int row) {
        if (asciiCode >= 48 && asciiCode <= 57) {
            return LedMatrixFont.digitPixel(asciiCode - 48, col, row);
        }
        if (asciiCode >= 65 && asciiCode <= 90) {
            return LedMatrixFont.letterPixel(asciiCode - 65, col, row);
        }
        return isSet(charRowBits(asciiCode, row), col);
    }

    private static boolean isSet(int rowBits, int col) {
        return ((rowBits >> (GLYPH_WIDTH - 1 - col)) & 1) != 0;
    }

    private static int charRowBits(int asciiCode, int row) {
        if (asciiCode < 40) {
            return LedMatrixSymbolsSpaceToApostrophe.rowBits(asciiCode, row);
        }
        if (asciiCode < 48) {
            return LedMatrixSymbolsParenToSlash.rowBits(asciiCode, row);
        }
        if (asciiCode < 65) {
            return LedMatrixSymbolsColonToAt.rowBits(asciiCode, row);
        }
        if (asciiCode < 97) {
            return LedMatrixSymbolsBracketToGrave.rowBits(asciiCode, row);
        }
        if (asciiCode < 123) {
            return lowerRowBits(asciiCode - 97, row);
        }
        return LedMatrixSymbolsBraceToTilde.rowBits(asciiCode, row);
    }

    private static int lowerRowBits(int letter, int row) {
        if (letter < 7) {
            return LedMatrixLowerAtoG.rowBits(letter, row);
        }
        if (letter < 14) {
            return LedMatrixLowerHtoN.rowBits(letter - 7, row);
        }
        if (letter < 20) {
            return LedMatrixLowerOtoT.rowBits(letter - 14, row);
        }
        return LedMatrixLowerUtoZ.rowBits(letter - 20, row);
    }
}
