package io.github.jabrena.juno.api.led;

/** Row-bit tables for uppercase letters U-Z, split out of {@link LedMatrixFont} to keep per-class cyclomatic complexity down. */
final class LedMatrixLettersUtoZ {
    private LedMatrixLettersUtoZ() {
    }

    /** letter: local index within this shard (0 = first letter of its range). row: 0 (top) to 6 (bottom). */
    static int rowBits(int letter, int row) {
        if (letter == 0) {
            return letterURowBits(row);
        }
        if (letter == 1) {
            return letterVRowBits(row);
        }
        if (letter == 2) {
            return letterWRowBits(row);
        }
        if (letter == 3) {
            return letterXRowBits(row);
        }
        if (letter == 4) {
            return letterYRowBits(row);
        }
        return letterZRowBits(row);
    }

    private static int letterURowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
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
            return 0b10001;
        }
        return 0b01110;
    }

    private static int letterVRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
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
        return 0b00100;
    }

    private static int letterWRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b10101;
        }
        if (row == 5) {
            return 0b11011;
        }
        return 0b10001;
    }

    private static int letterXRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b01010;
        }
        if (row == 3) {
            return 0b00100;
        }
        if (row == 4) {
            return 0b01010;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }

    private static int letterYRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b01010;
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
        return 0b00100;
    }

    private static int letterZRowBits(int row) {
        if (row == 0) {
            return 0b11111;
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
        return 0b11111;
    }
}
