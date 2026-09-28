package io.github.jabrena.juno.api.led;

/** Row-bit tables for ASCII 91-96 ([, \\, ], ^, _, `), split out of {@link LedMatrixFontAscii}. */
final class LedMatrixSymbolsBracketToGrave {
    private LedMatrixSymbolsBracketToGrave() {
    }

    static int rowBits(int asciiCode, int row) {
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
}
