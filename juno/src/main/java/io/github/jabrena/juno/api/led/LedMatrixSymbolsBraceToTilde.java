package io.github.jabrena.juno.api.led;

/** Row-bit tables for ASCII 123-126 ({, |, }, ~), split out of {@link LedMatrixFontAscii}. */
final class LedMatrixSymbolsBraceToTilde {
    private LedMatrixSymbolsBraceToTilde() {
    }

    static int rowBits(int asciiCode, int row) {
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
