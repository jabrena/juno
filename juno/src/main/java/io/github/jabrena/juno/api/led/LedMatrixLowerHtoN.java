package io.github.jabrena.juno.api.led;

/** Row-bit tables for lowercase letters h-n, split out of {@link LedMatrixFontAscii} to keep per-class cyclomatic complexity down. */
final class LedMatrixLowerHtoN {
    private LedMatrixLowerHtoN() {
    }

    /** letter: local index within this shard. row: 0 (top) to 6 (bottom). */
    static int rowBits(int letter, int row) {
        if (letter == 0) {
            return lowerHRowBits(row);
        }
        if (letter == 1) {
            return lowerIRowBits(row);
        }
        if (letter == 2) {
            return lowerJRowBits(row);
        }
        if (letter == 3) {
            return lowerKRowBits(row);
        }
        if (letter == 4) {
            return lowerLRowBits(row);
        }
        if (letter == 5) {
            return lowerMRowBits(row);
        }
        return lowerNRowBits(row);
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
}
