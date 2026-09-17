package io.github.jabrena.juno.api;

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
        if (asciiCode < 48) {
            return lowSymbolRowBits(asciiCode, row);
        }
        if (asciiCode < 65) {
            return midSymbolRowBits(asciiCode, row);
        }
        if (asciiCode < 97) {
            return highSymbolRowBits(asciiCode, row);
        }
        if (asciiCode < 123) {
            return lowerRowBits(asciiCode - 97, row);
        }
        return topSymbolRowBits(asciiCode, row);
    }

    // ---- 32-47: space and low punctuation ----

    private static int lowSymbolRowBits(int asciiCode, int row) {
        if (asciiCode == 32) {
            return symbolSpaceRowBits(row);
        }
        if (asciiCode == 33) {
            return symbolExclamationRowBits(row);
        }
        if (asciiCode == 34) {
            return symbolQuoteRowBits(row);
        }
        if (asciiCode == 35) {
            return symbolHashRowBits(row);
        }
        if (asciiCode == 36) {
            return symbolDollarRowBits(row);
        }
        if (asciiCode == 37) {
            return symbolPercentRowBits(row);
        }
        if (asciiCode == 38) {
            return symbolAmpersandRowBits(row);
        }
        if (asciiCode == 39) {
            return symbolApostropheRowBits(row);
        }
        if (asciiCode == 40) {
            return symbolLeftParenRowBits(row);
        }
        if (asciiCode == 41) {
            return symbolRightParenRowBits(row);
        }
        if (asciiCode == 42) {
            return symbolAsteriskRowBits(row);
        }
        if (asciiCode == 43) {
            return symbolPlusRowBits(row);
        }
        if (asciiCode == 44) {
            return symbolCommaRowBits(row);
        }
        if (asciiCode == 45) {
            return symbolHyphenRowBits(row);
        }
        if (asciiCode == 46) {
            return symbolPeriodRowBits(row);
        }
        return symbolSlashRowBits(row);
    }

    private static int symbolSpaceRowBits(int row) {
        return 0b00000;
    }

    private static int symbolExclamationRowBits(int row) {
        if (row == 0) {
            return 0b00100;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b00100;
        }
        if (row == 3) {
            return 0b00100;
        }
        if (row == 4) {
            return 0b00000;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b00000;
    }

    private static int symbolQuoteRowBits(int row) {
        if (row == 0) {
            return 0b01010;
        }
        if (row == 1) {
            return 0b01010;
        }
        return 0b00000;
    }

    private static int symbolHashRowBits(int row) {
        if (row == 0) {
            return 0b01010;
        }
        if (row == 1) {
            return 0b01010;
        }
        if (row == 2) {
            return 0b11111;
        }
        if (row == 3) {
            return 0b01010;
        }
        if (row == 4) {
            return 0b11111;
        }
        if (row == 5) {
            return 0b01010;
        }
        return 0b01010;
    }

    private static int symbolDollarRowBits(int row) {
        if (row == 0) {
            return 0b00100;
        }
        if (row == 1) {
            return 0b01111;
        }
        if (row == 2) {
            return 0b10100;
        }
        if (row == 3) {
            return 0b01110;
        }
        if (row == 4) {
            return 0b00101;
        }
        if (row == 5) {
            return 0b11110;
        }
        return 0b00100;
    }

    private static int symbolPercentRowBits(int row) {
        if (row == 0) {
            return 0b11001;
        }
        if (row == 1) {
            return 0b11010;
        }
        if (row == 2) {
            return 0b00010;
        }
        if (row == 3) {
            return 0b00100;
        }
        if (row == 4) {
            return 0b01000;
        }
        if (row == 5) {
            return 0b01011;
        }
        return 0b10011;
    }

    private static int symbolAmpersandRowBits(int row) {
        if (row == 0) {
            return 0b01100;
        }
        if (row == 1) {
            return 0b10010;
        }
        if (row == 2) {
            return 0b10100;
        }
        if (row == 3) {
            return 0b01000;
        }
        if (row == 4) {
            return 0b10101;
        }
        if (row == 5) {
            return 0b10010;
        }
        return 0b01101;
    }

    private static int symbolApostropheRowBits(int row) {
        if (row == 0) {
            return 0b00100;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b01000;
        }
        return 0b00000;
    }

    private static int symbolLeftParenRowBits(int row) {
        if (row == 0) {
            return 0b00010;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b01000;
        }
        if (row == 3) {
            return 0b01000;
        }
        if (row == 4) {
            return 0b01000;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b00010;
    }

    private static int symbolRightParenRowBits(int row) {
        if (row == 0) {
            return 0b01000;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b00010;
        }
        if (row == 3) {
            return 0b00010;
        }
        if (row == 4) {
            return 0b00010;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b01000;
    }

    private static int symbolAsteriskRowBits(int row) {
        if (row == 0) {
            return 0b00000;
        }
        if (row == 1) {
            return 0b10101;
        }
        if (row == 2) {
            return 0b01110;
        }
        if (row == 3) {
            return 0b11111;
        }
        if (row == 4) {
            return 0b01110;
        }
        if (row == 5) {
            return 0b10101;
        }
        return 0b00000;
    }

    private static int symbolPlusRowBits(int row) {
        if (row == 0) {
            return 0b00000;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b00100;
        }
        if (row == 3) {
            return 0b11111;
        }
        if (row == 4) {
            return 0b00100;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b00000;
    }

    private static int symbolCommaRowBits(int row) {
        if (row == 0) {
            return 0b00000;
        }
        if (row == 1) {
            return 0b00000;
        }
        if (row == 2) {
            return 0b00000;
        }
        if (row == 3) {
            return 0b00000;
        }
        if (row == 4) {
            return 0b00110;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b01000;
    }

    private static int symbolHyphenRowBits(int row) {
        if (row == 3) {
            return 0b11111;
        }
        return 0b00000;
    }

    private static int symbolPeriodRowBits(int row) {
        if (row == 5) {
            return 0b01100;
        }
        if (row == 6) {
            return 0b01100;
        }
        return 0b00000;
    }

    private static int symbolSlashRowBits(int row) {
        if (row == 0) {
            return 0b00001;
        }
        if (row == 1) {
            return 0b00001;
        }
        if (row == 2) {
            return 0b00010;
        }
        if (row == 3) {
            return 0b00100;
        }
        if (row == 4) {
            return 0b01000;
        }
        if (row == 5) {
            return 0b10000;
        }
        return 0b10000;
    }

    // ---- 58-64: mid punctuation ----

    private static int midSymbolRowBits(int asciiCode, int row) {
        if (asciiCode == 58) {
            return symbolColonRowBits(row);
        }
        if (asciiCode == 59) {
            return symbolSemicolonRowBits(row);
        }
        if (asciiCode == 60) {
            return symbolLessThanRowBits(row);
        }
        if (asciiCode == 61) {
            return symbolEqualsRowBits(row);
        }
        if (asciiCode == 62) {
            return symbolGreaterThanRowBits(row);
        }
        if (asciiCode == 63) {
            return symbolQuestionRowBits(row);
        }
        return symbolAtRowBits(row);
    }

    private static int symbolColonRowBits(int row) {
        if (row == 1) {
            return 0b01100;
        }
        if (row == 2) {
            return 0b01100;
        }
        if (row == 4) {
            return 0b01100;
        }
        if (row == 5) {
            return 0b01100;
        }
        return 0b00000;
    }

    private static int symbolSemicolonRowBits(int row) {
        if (row == 1) {
            return 0b01100;
        }
        if (row == 2) {
            return 0b01100;
        }
        if (row == 4) {
            return 0b00110;
        }
        if (row == 5) {
            return 0b00100;
        }
        if (row == 6) {
            return 0b01000;
        }
        return 0b00000;
    }

    private static int symbolLessThanRowBits(int row) {
        if (row == 0) {
            return 0b00010;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b01000;
        }
        if (row == 3) {
            return 0b10000;
        }
        if (row == 4) {
            return 0b01000;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b00010;
    }

    private static int symbolEqualsRowBits(int row) {
        if (row == 2) {
            return 0b11111;
        }
        if (row == 4) {
            return 0b11111;
        }
        return 0b00000;
    }

    private static int symbolGreaterThanRowBits(int row) {
        if (row == 0) {
            return 0b01000;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b00010;
        }
        if (row == 3) {
            return 0b00001;
        }
        if (row == 4) {
            return 0b00010;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b01000;
    }

    private static int symbolQuestionRowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b00001;
        }
        if (row == 3) {
            return 0b00010;
        }
        if (row == 4) {
            return 0b00100;
        }
        if (row == 5) {
            return 0b00000;
        }
        return 0b00100;
    }

    private static int symbolAtRowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10111;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b10111;
        }
        if (row == 5) {
            return 0b10000;
        }
        return 0b01110;
    }

    // ---- 91-96: high punctuation ----

    private static int highSymbolRowBits(int asciiCode, int row) {
        if (asciiCode == 91) {
            return symbolLeftBracketRowBits(row);
        }
        if (asciiCode == 92) {
            return symbolBackslashRowBits(row);
        }
        if (asciiCode == 93) {
            return symbolRightBracketRowBits(row);
        }
        if (asciiCode == 94) {
            return symbolCaretRowBits(row);
        }
        if (asciiCode == 95) {
            return symbolUnderscoreRowBits(row);
        }
        return symbolGraveRowBits(row);
    }

    private static int symbolLeftBracketRowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 6) {
            return 0b01110;
        }
        return 0b01000;
    }

    private static int symbolBackslashRowBits(int row) {
        if (row == 0) {
            return 0b10000;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b01000;
        }
        if (row == 3) {
            return 0b00100;
        }
        if (row == 4) {
            return 0b00010;
        }
        if (row == 5) {
            return 0b00001;
        }
        return 0b00001;
    }

    private static int symbolRightBracketRowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 6) {
            return 0b01110;
        }
        return 0b00010;
    }

    private static int symbolCaretRowBits(int row) {
        if (row == 0) {
            return 0b00100;
        }
        if (row == 1) {
            return 0b01010;
        }
        if (row == 2) {
            return 0b10001;
        }
        return 0b00000;
    }

    private static int symbolUnderscoreRowBits(int row) {
        if (row == 6) {
            return 0b11111;
        }
        return 0b00000;
    }

    private static int symbolGraveRowBits(int row) {
        if (row == 0) {
            return 0b01000;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b00010;
        }
        return 0b00000;
    }

    // ---- 97-122: lowercase letters ----

    private static int lowerRowBits(int letter, int row) {
        if (letter == 0) {
            return lowerARowBits(row);
        }
        if (letter == 1) {
            return lowerBRowBits(row);
        }
        if (letter == 2) {
            return lowerCRowBits(row);
        }
        if (letter == 3) {
            return lowerDRowBits(row);
        }
        if (letter == 4) {
            return lowerERowBits(row);
        }
        if (letter == 5) {
            return lowerFRowBits(row);
        }
        if (letter == 6) {
            return lowerGRowBits(row);
        }
        if (letter == 7) {
            return lowerHRowBits(row);
        }
        if (letter == 8) {
            return lowerIRowBits(row);
        }
        if (letter == 9) {
            return lowerJRowBits(row);
        }
        if (letter == 10) {
            return lowerKRowBits(row);
        }
        if (letter == 11) {
            return lowerLRowBits(row);
        }
        if (letter == 12) {
            return lowerMRowBits(row);
        }
        if (letter == 13) {
            return lowerNRowBits(row);
        }
        if (letter == 14) {
            return lowerORowBits(row);
        }
        if (letter == 15) {
            return lowerPRowBits(row);
        }
        if (letter == 16) {
            return lowerQRowBits(row);
        }
        if (letter == 17) {
            return lowerRRowBits(row);
        }
        if (letter == 18) {
            return lowerSRowBits(row);
        }
        if (letter == 19) {
            return lowerTRowBits(row);
        }
        if (letter == 20) {
            return lowerURowBits(row);
        }
        if (letter == 21) {
            return lowerVRowBits(row);
        }
        if (letter == 22) {
            return lowerWRowBits(row);
        }
        if (letter == 23) {
            return lowerXRowBits(row);
        }
        if (letter == 24) {
            return lowerYRowBits(row);
        }
        return lowerZRowBits(row);
    }

    private static int lowerARowBits(int row) {
        if (row == 2) {
            return 0b01110;
        }
        if (row == 3) {
            return 0b00001;
        }
        if (row == 4) {
            return 0b01111;
        }
        if (row == 5) {
            return 0b10001;
        }
        if (row == 6) {
            return 0b01111;
        }
        return 0b00000;
    }

    private static int lowerBRowBits(int row) {
        if (row == 0) {
            return 0b10000;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b11110;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b11110;
    }

    private static int lowerCRowBits(int row) {
        if (row == 2) {
            return 0b01111;
        }
        if (row == 3) {
            return 0b10000;
        }
        if (row == 4) {
            return 0b10000;
        }
        if (row == 5) {
            return 0b10000;
        }
        if (row == 6) {
            return 0b01111;
        }
        return 0b00000;
    }

    private static int lowerDRowBits(int row) {
        if (row == 0) {
            return 0b00001;
        }
        if (row == 1) {
            return 0b00001;
        }
        if (row == 2) {
            return 0b01111;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01111;
    }

    private static int lowerERowBits(int row) {
        if (row == 2) {
            return 0b01110;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b11111;
        }
        if (row == 5) {
            return 0b10000;
        }
        if (row == 6) {
            return 0b01111;
        }
        return 0b00000;
    }

    private static int lowerFRowBits(int row) {
        if (row == 0) {
            return 0b00110;
        }
        if (row == 1) {
            return 0b01001;
        }
        if (row == 2) {
            return 0b01000;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b01000;
        }
        if (row == 5) {
            return 0b01000;
        }
        return 0b01000;
    }

    private static int lowerGRowBits(int row) {
        if (row == 2) {
            return 0b01110;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b01111;
        }
        return 0b00001;
    }

    private static int lowerHRowBits(int row) {
        if (row == 0) {
            return 0b10000;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10110;
        }
        if (row == 3) {
            return 0b11001;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }

    private static int lowerIRowBits(int row) {
        if (row == 0) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b01100;
        }
        if (row == 3) {
            return 0b00100;
        }
        if (row == 4) {
            return 0b00100;
        }
        if (row == 5) {
            return 0b00100;
        }
        if (row == 6) {
            return 0b01110;
        }
        return 0b00000;
    }

    private static int lowerJRowBits(int row) {
        if (row == 0) {
            return 0b00010;
        }
        if (row == 2) {
            return 0b00110;
        }
        if (row == 3) {
            return 0b00010;
        }
        if (row == 4) {
            return 0b00010;
        }
        if (row == 5) {
            return 0b10010;
        }
        if (row == 6) {
            return 0b01100;
        }
        return 0b00000;
    }

    private static int lowerKRowBits(int row) {
        if (row == 0) {
            return 0b10000;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10010;
        }
        if (row == 3) {
            return 0b10100;
        }
        if (row == 4) {
            return 0b11000;
        }
        if (row == 5) {
            return 0b10100;
        }
        return 0b10010;
    }

    private static int lowerLRowBits(int row) {
        if (row == 0) {
            return 0b01100;
        }
        if (row == 6) {
            return 0b01110;
        }
        return 0b00100;
    }

    private static int lowerMRowBits(int row) {
        if (row == 2) {
            return 0b11010;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b10101;
        }
        if (row == 5) {
            return 0b10101;
        }
        if (row == 6) {
            return 0b10101;
        }
        return 0b00000;
    }

    private static int lowerNRowBits(int row) {
        if (row == 2) {
            return 0b10110;
        }
        if (row == 3) {
            return 0b11001;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        if (row == 6) {
            return 0b10001;
        }
        return 0b00000;
    }

    private static int lowerORowBits(int row) {
        if (row == 2) {
            return 0b01110;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        if (row == 6) {
            return 0b01110;
        }
        return 0b00000;
    }

    private static int lowerPRowBits(int row) {
        if (row == 2) {
            return 0b11110;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b11110;
        }
        if (row == 5) {
            return 0b10000;
        }
        if (row == 6) {
            return 0b10000;
        }
        return 0b00000;
    }

    private static int lowerQRowBits(int row) {
        if (row == 2) {
            return 0b01111;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b01111;
        }
        if (row == 5) {
            return 0b00001;
        }
        if (row == 6) {
            return 0b00001;
        }
        return 0b00000;
    }

    private static int lowerRRowBits(int row) {
        if (row == 2) {
            return 0b10110;
        }
        if (row == 3) {
            return 0b11001;
        }
        if (row == 4) {
            return 0b10000;
        }
        if (row == 5) {
            return 0b10000;
        }
        if (row == 6) {
            return 0b10000;
        }
        return 0b00000;
    }

    private static int lowerSRowBits(int row) {
        if (row == 2) {
            return 0b01111;
        }
        if (row == 3) {
            return 0b10000;
        }
        if (row == 4) {
            return 0b01110;
        }
        if (row == 5) {
            return 0b00001;
        }
        if (row == 6) {
            return 0b11110;
        }
        return 0b00000;
    }

    private static int lowerTRowBits(int row) {
        if (row == 0) {
            return 0b01000;
        }
        if (row == 1) {
            return 0b01000;
        }
        if (row == 2) {
            return 0b11110;
        }
        if (row == 3) {
            return 0b01000;
        }
        if (row == 4) {
            return 0b01000;
        }
        if (row == 5) {
            return 0b01001;
        }
        return 0b00110;
    }

    private static int lowerURowBits(int row) {
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10011;
        }
        if (row == 6) {
            return 0b01101;
        }
        return 0b00000;
    }

    private static int lowerVRowBits(int row) {
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b01010;
        }
        if (row == 6) {
            return 0b00100;
        }
        return 0b00000;
    }

    private static int lowerWRowBits(int row) {
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b10101;
        }
        if (row == 5) {
            return 0b10101;
        }
        if (row == 6) {
            return 0b01010;
        }
        return 0b00000;
    }

    private static int lowerXRowBits(int row) {
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b01010;
        }
        if (row == 4) {
            return 0b00100;
        }
        if (row == 5) {
            return 0b01010;
        }
        if (row == 6) {
            return 0b10001;
        }
        return 0b00000;
    }

    private static int lowerYRowBits(int row) {
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b01111;
        }
        if (row == 5) {
            return 0b00001;
        }
        if (row == 6) {
            return 0b01110;
        }
        return 0b00000;
    }

    private static int lowerZRowBits(int row) {
        if (row == 2) {
            return 0b11111;
        }
        if (row == 3) {
            return 0b00010;
        }
        if (row == 4) {
            return 0b00100;
        }
        if (row == 5) {
            return 0b01000;
        }
        if (row == 6) {
            return 0b11111;
        }
        return 0b00000;
    }

    // ---- 123-126: top punctuation ----

    private static int topSymbolRowBits(int asciiCode, int row) {
        if (asciiCode == 123) {
            return symbolLeftBraceRowBits(row);
        }
        if (asciiCode == 124) {
            return symbolPipeRowBits(row);
        }
        if (asciiCode == 125) {
            return symbolRightBraceRowBits(row);
        }
        return symbolTildeRowBits(row);
    }

    private static int symbolLeftBraceRowBits(int row) {
        if (row == 0) {
            return 0b00011;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b00100;
        }
        if (row == 3) {
            return 0b01000;
        }
        if (row == 4) {
            return 0b00100;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b00011;
    }

    private static int symbolPipeRowBits(int row) {
        return 0b00100;
    }

    private static int symbolRightBraceRowBits(int row) {
        if (row == 0) {
            return 0b11000;
        }
        if (row == 1) {
            return 0b00100;
        }
        if (row == 2) {
            return 0b00100;
        }
        if (row == 3) {
            return 0b00010;
        }
        if (row == 4) {
            return 0b00100;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b11000;
    }

    private static int symbolTildeRowBits(int row) {
        if (row == 2) {
            return 0b01001;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b10010;
        }
        return 0b00000;
    }
}
