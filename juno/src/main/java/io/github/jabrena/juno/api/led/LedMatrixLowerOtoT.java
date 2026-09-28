package io.github.jabrena.juno.api.led;

/** Row-bit tables for lowercase letters o-t, split out of {@link LedMatrixFontAscii} to keep per-class cyclomatic complexity down. */
final class LedMatrixLowerOtoT {
    private LedMatrixLowerOtoT() {
    }

    /** letter: local index within this shard. row: 0 (top) to 6 (bottom). */
    static int rowBits(int letter, int row) {
        if (letter == 0) {
            return lowerORowBits(row);
        }
        if (letter == 1) {
            return lowerPRowBits(row);
        }
        if (letter == 2) {
            return lowerQRowBits(row);
        }
        if (letter == 3) {
            return lowerRRowBits(row);
        }
        if (letter == 4) {
            return lowerSRowBits(row);
        }
        return lowerTRowBits(row);
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
}
