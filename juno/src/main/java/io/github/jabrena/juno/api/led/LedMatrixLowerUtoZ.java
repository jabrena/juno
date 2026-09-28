package io.github.jabrena.juno.api.led;

/** Row-bit tables for lowercase letters u-z, split out of {@link LedMatrixFontAscii} to keep per-class cyclomatic complexity down. */
final class LedMatrixLowerUtoZ {
    private LedMatrixLowerUtoZ() {
    }

    /** letter: local index within this shard. row: 0 (top) to 6 (bottom). */
    static int rowBits(int letter, int row) {
        if (letter == 0) {
            return lowerURowBits(row);
        }
        if (letter == 1) {
            return lowerVRowBits(row);
        }
        if (letter == 2) {
            return lowerWRowBits(row);
        }
        if (letter == 3) {
            return lowerXRowBits(row);
        }
        if (letter == 4) {
            return lowerYRowBits(row);
        }
        return lowerZRowBits(row);
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
}
