package io.github.jabrena.juno.api.led;

/** Row-bit tables for ASCII 58-64 (:, ;, &lt;, =, &gt;, ?, \@), split out of {@link LedMatrixFontAscii}. */
final class LedMatrixSymbolsColonToAt {
    private LedMatrixSymbolsColonToAt() {
    }

    static int rowBits(int asciiCode, int row) {
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
}
