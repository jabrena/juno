package io.github.jabrena.juno.api;

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

    private static int digitRowBits(int digit, int row) {
        if (digit == 0) {
            return digit0RowBits(row);
        }
        if (digit == 1) {
            return digit1RowBits(row);
        }
        if (digit == 2) {
            return digit2RowBits(row);
        }
        if (digit == 3) {
            return digit3RowBits(row);
        }
        if (digit == 4) {
            return digit4RowBits(row);
        }
        if (digit == 5) {
            return digit5RowBits(row);
        }
        if (digit == 6) {
            return digit6RowBits(row);
        }
        if (digit == 7) {
            return digit7RowBits(row);
        }
        if (digit == 8) {
            return digit8RowBits(row);
        }
        return digit9RowBits(row);
    }

    private static int digit0RowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10011;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b11001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01110;
    }

    private static int digit1RowBits(int row) {
        if (row == 0) {
            return 0b00100;
        }
        if (row == 1) {
            return 0b01100;
        }
        if (row == 2) {
            return 0b00100;
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
        return 0b01110;
    }

    private static int digit2RowBits(int row) {
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
            return 0b01000;
        }
        return 0b11111;
    }

    private static int digit3RowBits(int row) {
        if (row == 0) {
            return 0b11111;
        }
        if (row == 1) {
            return 0b00010;
        }
        if (row == 2) {
            return 0b00100;
        }
        if (row == 3) {
            return 0b00010;
        }
        if (row == 4) {
            return 0b00001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01110;
    }

    private static int digit4RowBits(int row) {
        if (row == 0) {
            return 0b00010;
        }
        if (row == 1) {
            return 0b00110;
        }
        if (row == 2) {
            return 0b01010;
        }
        if (row == 3) {
            return 0b10010;
        }
        if (row == 4) {
            return 0b11111;
        }
        if (row == 5) {
            return 0b00010;
        }
        return 0b00010;
    }

    private static int digit5RowBits(int row) {
        if (row == 0) {
            return 0b11111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b11110;
        }
        if (row == 3) {
            return 0b00001;
        }
        if (row == 4) {
            return 0b00001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01110;
    }

    private static int digit6RowBits(int row) {
        if (row == 0) {
            return 0b00110;
        }
        if (row == 1) {
            return 0b01000;
        }
        if (row == 2) {
            return 0b10000;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01110;
    }

    private static int digit7RowBits(int row) {
        if (row == 0) {
            return 0b11111;
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
            return 0b01000;
        }
        return 0b01000;
    }

    private static int digit8RowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b01110;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01110;
    }

    private static int digit9RowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b01111;
        }
        if (row == 4) {
            return 0b00001;
        }
        if (row == 5) {
            return 0b00010;
        }
        return 0b01100;
    }

    private static int letterRowBits(int letter, int row) {
        if (letter == 0) {
            return letterARowBits(row);
        }
        if (letter == 1) {
            return letterBRowBits(row);
        }
        if (letter == 2) {
            return letterCRowBits(row);
        }
        if (letter == 3) {
            return letterDRowBits(row);
        }
        if (letter == 4) {
            return letterERowBits(row);
        }
        if (letter == 5) {
            return letterFRowBits(row);
        }
        if (letter == 6) {
            return letterGRowBits(row);
        }
        if (letter == 7) {
            return letterHRowBits(row);
        }
        if (letter == 8) {
            return letterIRowBits(row);
        }
        if (letter == 9) {
            return letterJRowBits(row);
        }
        if (letter == 10) {
            return letterKRowBits(row);
        }
        if (letter == 11) {
            return letterLRowBits(row);
        }
        if (letter == 12) {
            return letterMRowBits(row);
        }
        if (letter == 13) {
            return letterNRowBits(row);
        }
        if (letter == 14) {
            return letterORowBits(row);
        }
        if (letter == 15) {
            return letterPRowBits(row);
        }
        if (letter == 16) {
            return letterQRowBits(row);
        }
        if (letter == 17) {
            return letterRRowBits(row);
        }
        if (letter == 18) {
            return letterSRowBits(row);
        }
        if (letter == 19) {
            return letterTRowBits(row);
        }
        if (letter == 20) {
            return letterURowBits(row);
        }
        if (letter == 21) {
            return letterVRowBits(row);
        }
        if (letter == 22) {
            return letterWRowBits(row);
        }
        if (letter == 23) {
            return letterXRowBits(row);
        }
        if (letter == 24) {
            return letterYRowBits(row);
        }
        return letterZRowBits(row);
    }

    private static int letterARowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b11111;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }

    private static int letterBRowBits(int row) {
        if (row == 0) {
            return 0b11110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b11110;
    }

    private static int letterCRowBits(int row) {
        if (row == 0) {
            return 0b01111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10000;
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
        return 0b01111;
    }

    private static int letterDRowBits(int row) {
        if (row == 0) {
            return 0b11110;
        }
        if (row == 1) {
            return 0b10001;
        }
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
            return 0b10001;
        }
        return 0b11110;
    }

    private static int letterERowBits(int row) {
        if (row == 0) {
            return 0b11111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10000;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b10000;
        }
        if (row == 5) {
            return 0b10000;
        }
        return 0b11111;
    }

    private static int letterFRowBits(int row) {
        if (row == 0) {
            return 0b11111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10000;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b10000;
        }
        if (row == 5) {
            return 0b10000;
        }
        return 0b10000;
    }

    private static int letterGRowBits(int row) {
        if (row == 0) {
            return 0b01111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10000;
        }
        if (row == 3) {
            return 0b10111;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01111;
    }

    private static int letterHRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b11111;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }

    private static int letterIRowBits(int row) {
        if (row == 0) {
            return 0b01110;
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
            return 0b00100;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b01110;
    }

    private static int letterJRowBits(int row) {
        if (row == 0) {
            return 0b00111;
        }
        if (row == 1) {
            return 0b00010;
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
            return 0b10010;
        }
        return 0b01100;
    }

    private static int letterKRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10010;
        }
        if (row == 2) {
            return 0b10100;
        }
        if (row == 3) {
            return 0b11000;
        }
        if (row == 4) {
            return 0b10100;
        }
        if (row == 5) {
            return 0b10010;
        }
        return 0b10001;
    }

    private static int letterLRowBits(int row) {
        if (row == 0) {
            return 0b10000;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10000;
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
        return 0b11111;
    }

    private static int letterMRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b11011;
        }
        if (row == 2) {
            return 0b10101;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }

    private static int letterNRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b11001;
        }
        if (row == 2) {
            return 0b10101;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b10011;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }

    private static int letterORowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
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
            return 0b10001;
        }
        return 0b01110;
    }

    private static int letterPRowBits(int row) {
        if (row == 0) {
            return 0b11110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b10000;
        }
        if (row == 5) {
            return 0b10000;
        }
        return 0b10000;
    }

    private static int letterQRowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
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
            return 0b10010;
        }
        return 0b01101;
    }

    private static int letterRRowBits(int row) {
        if (row == 0) {
            return 0b11110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b10100;
        }
        if (row == 5) {
            return 0b10010;
        }
        return 0b10001;
    }

    private static int letterSRowBits(int row) {
        if (row == 0) {
            return 0b01111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10000;
        }
        if (row == 3) {
            return 0b01110;
        }
        if (row == 4) {
            return 0b00001;
        }
        if (row == 5) {
            return 0b00001;
        }
        return 0b11110;
    }

    private static int letterTRowBits(int row) {
        if (row == 0) {
            return 0b11111;
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
            return 0b00100;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b00100;
    }

    private static int letterURowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
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
            return 0b10001;
        }
        return 0b01110;
    }

    private static int letterVRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
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
        return 0b00100;
    }

    private static int letterWRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b10101;
        }
        if (row == 5) {
            return 0b11011;
        }
        return 0b10001;
    }

    private static int letterXRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b01010;
        }
        if (row == 3) {
            return 0b00100;
        }
        if (row == 4) {
            return 0b01010;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }

    private static int letterYRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b01010;
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
        return 0b00100;
    }

    private static int letterZRowBits(int row) {
        if (row == 0) {
            return 0b11111;
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
        return 0b11111;
    }
}
