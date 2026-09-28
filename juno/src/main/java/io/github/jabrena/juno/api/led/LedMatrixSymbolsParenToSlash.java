package io.github.jabrena.juno.api.led;

/** Row-bit tables for ASCII 40-47 ((, ), *, +, comma, -, ., /), split out of {@link LedMatrixFontAscii}. */
final class LedMatrixSymbolsParenToSlash {
    private LedMatrixSymbolsParenToSlash() {
    }

    static int rowBits(int asciiCode, int row) {
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
}
