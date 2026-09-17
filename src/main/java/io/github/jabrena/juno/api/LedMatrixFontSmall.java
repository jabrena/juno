package io.github.jabrena.juno.api;

/**
 * A compact 3x5 dot-matrix font for digits 0-9, offered as a smaller alternative to the 5x7
 * {@link LedMatrixFont} (which it does not replace). It is sized so a one-decimal-digit reading
 * like "2.5" fits comfortably on the UNO R4 WiFi's 12x8 LED matrix: digit + gap + decimal point +
 * gap + digit = 3 + 1 + 1 + 1 + 3 = 9 of the 12 columns.
 *
 * <p>As with {@link LedMatrixFont}, Juno v0.1 has no arrays, so each glyph is encoded as a small
 * function returning, for a given row (0 = top .. 4 = bottom), a 3-bit mask of its lit columns
 * (bit 2 = leftmost column).
 */
public final class LedMatrixFontSmall {
    public static final int GLYPH_WIDTH = 3;
    public static final int GLYPH_HEIGHT = 5;

    private LedMatrixFontSmall() {
    }

    /** digit: 0-9. col: 0 (left) to {@link #GLYPH_WIDTH} - 1. row: 0 (top) to {@link #GLYPH_HEIGHT} - 1. */
    public static boolean digitPixel(int digit, int col, int row) {
        return isSet(digitRowBits(digit, row), col);
    }

    /** The decimal point: a single pixel, baseline-aligned with the last digit row. */
    public static boolean pointPixel(int row) {
        return row == GLYPH_HEIGHT - 1;
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
            return 0b111;
        }
        if (row == 1) {
            return 0b101;
        }
        if (row == 2) {
            return 0b101;
        }
        if (row == 3) {
            return 0b101;
        }
        return 0b111;
    }

    private static int digit1RowBits(int row) {
        if (row == 0) {
            return 0b010;
        }
        if (row == 1) {
            return 0b110;
        }
        if (row == 2) {
            return 0b010;
        }
        if (row == 3) {
            return 0b010;
        }
        return 0b111;
    }

    private static int digit2RowBits(int row) {
        if (row == 0) {
            return 0b111;
        }
        if (row == 1) {
            return 0b001;
        }
        if (row == 2) {
            return 0b111;
        }
        if (row == 3) {
            return 0b100;
        }
        return 0b111;
    }

    private static int digit3RowBits(int row) {
        if (row == 0) {
            return 0b111;
        }
        if (row == 1) {
            return 0b001;
        }
        if (row == 2) {
            return 0b111;
        }
        if (row == 3) {
            return 0b001;
        }
        return 0b111;
    }

    private static int digit4RowBits(int row) {
        if (row == 0) {
            return 0b101;
        }
        if (row == 1) {
            return 0b101;
        }
        if (row == 2) {
            return 0b111;
        }
        if (row == 3) {
            return 0b001;
        }
        return 0b001;
    }

    private static int digit5RowBits(int row) {
        if (row == 0) {
            return 0b111;
        }
        if (row == 1) {
            return 0b100;
        }
        if (row == 2) {
            return 0b111;
        }
        if (row == 3) {
            return 0b001;
        }
        return 0b111;
    }

    private static int digit6RowBits(int row) {
        if (row == 0) {
            return 0b111;
        }
        if (row == 1) {
            return 0b100;
        }
        if (row == 2) {
            return 0b111;
        }
        if (row == 3) {
            return 0b101;
        }
        return 0b111;
    }

    private static int digit7RowBits(int row) {
        if (row == 0) {
            return 0b111;
        }
        if (row == 1) {
            return 0b001;
        }
        if (row == 2) {
            return 0b001;
        }
        if (row == 3) {
            return 0b001;
        }
        return 0b001;
    }

    private static int digit8RowBits(int row) {
        if (row == 0) {
            return 0b111;
        }
        if (row == 1) {
            return 0b101;
        }
        if (row == 2) {
            return 0b111;
        }
        if (row == 3) {
            return 0b101;
        }
        return 0b111;
    }

    private static int digit9RowBits(int row) {
        if (row == 0) {
            return 0b111;
        }
        if (row == 1) {
            return 0b101;
        }
        if (row == 2) {
            return 0b111;
        }
        if (row == 3) {
            return 0b001;
        }
        return 0b111;
    }
}
