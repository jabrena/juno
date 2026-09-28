package io.github.jabrena.juno.api.led;

/** Row-bit tables for ASCII 32-39 (space, !, ", #, $, %, \&, '), split out of {@link LedMatrixFontAscii}. */
final class LedMatrixSymbolsSpaceToApostrophe {
    private LedMatrixSymbolsSpaceToApostrophe() {
    }

    static int rowBits(int asciiCode, int row) {
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
        return symbolApostropheRowBits(row);
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
}
