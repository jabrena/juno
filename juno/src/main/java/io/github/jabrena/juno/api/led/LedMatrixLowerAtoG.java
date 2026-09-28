package io.github.jabrena.juno.api.led;

/** Row-bit tables for lowercase letters a-g, split out of {@link LedMatrixFontAscii} to keep per-class cyclomatic complexity down. */
final class LedMatrixLowerAtoG {
    private LedMatrixLowerAtoG() {
    }

    /** letter: local index within this shard. row: 0 (top) to 6 (bottom). */
    static int rowBits(int letter, int row) {
        if (letter == 0) {
            return lowerARowBits(row);
        }
        if (letter == 1) {
            return lowerBRowBits(row);
        }
        if (letter == 2) {
            return lowerCRowBits(row);
        }
        if (letter == 3) {
            return lowerDRowBits(row);
        }
        if (letter == 4) {
            return lowerERowBits(row);
        }
        if (letter == 5) {
            return lowerFRowBits(row);
        }
        return lowerGRowBits(row);
    }

    private static int lowerARowBits(int row) {
        if (row == 2) {
            return 0b01110;
        }
        if (row == 3) {
            return 0b00001;
        }
        if (row == 4) {
            return 0b01111;
        }
        if (row == 5) {
            return 0b10001;
        }
        if (row == 6) {
            return 0b01111;
        }
        return 0b00000;
    }

    private static int lowerBRowBits(int row) {
        if (row == 0) {
            return 0b10000;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b11110;
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

    private static int lowerCRowBits(int row) {
        if (row == 2) {
            return 0b01111;
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
        if (row == 6) {
            return 0b01111;
        }
        return 0b00000;
    }

    private static int lowerDRowBits(int row) {
        if (row == 0) {
            return 0b00001;
        }
        if (row == 1) {
            return 0b00001;
        }
        if (row == 2) {
            return 0b01111;
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
        return 0b01111;
    }

    private static int lowerERowBits(int row) {
        if (row == 2) {
            return 0b01110;
        }
        if (row == 3) {
            return 0b10001;
        }
        if (row == 4) {
            return 0b11111;
        }
        if (row == 5) {
            return 0b10000;
        }
        if (row == 6) {
            return 0b01111;
        }
        return 0b00000;
    }

    private static int lowerFRowBits(int row) {
        if (row == 0) {
            return 0b00110;
        }
        if (row == 1) {
            return 0b01001;
        }
        if (row == 2) {
            return 0b01000;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b01000;
        }
        if (row == 5) {
            return 0b01000;
        }
        return 0b01000;
    }

    private static int lowerGRowBits(int row) {
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
            return 0b01111;
        }
        return 0b00001;
    }
}
