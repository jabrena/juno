package io.github.jabrena.juno.api.led;

/** Row-bit tables for uppercase letters O-T, split out of {@link LedMatrixFont} to keep per-class cyclomatic complexity down. */
final class LedMatrixLettersOtoT {
    private LedMatrixLettersOtoT() {
    }

    /** letter: local index within this shard (0 = first letter of its range). row: 0 (top) to 6 (bottom). */
    static int rowBits(int letter, int row) {
        if (letter == 0) {
            return letterORowBits(row);
        }
        if (letter == 1) {
            return letterPRowBits(row);
        }
        if (letter == 2) {
            return letterQRowBits(row);
        }
        if (letter == 3) {
            return letterRRowBits(row);
        }
        if (letter == 4) {
            return letterSRowBits(row);
        }
        return letterTRowBits(row);
    }

    private static int letterORowBits(int row) {
        if (row == 0) {
            return 0b01110;
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

    private static int letterPRowBits(int row) {
        if (row == 0) {
            return 0b11110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b10000;
        }
        if (row == 5) {
            return 0b10000;
        }
        return 0b10000;
    }

    private static int letterQRowBits(int row) {
        if (row == 0) {
            return 0b01110;
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
            return 0b10101;
        }
        if (row == 5) {
            return 0b10010;
        }
        return 0b01101;
    }

    private static int letterRRowBits(int row) {
        if (row == 0) {
            return 0b11110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10001;
        }
        if (row == 3) {
            return 0b11110;
        }
        if (row == 4) {
            return 0b10100;
        }
        if (row == 5) {
            return 0b10010;
        }
        return 0b10001;
    }

    private static int letterSRowBits(int row) {
        if (row == 0) {
            return 0b01111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10000;
        }
        if (row == 3) {
            return 0b01110;
        }
        if (row == 4) {
            return 0b00001;
        }
        if (row == 5) {
            return 0b00001;
        }
        return 0b11110;
    }

    private static int letterTRowBits(int row) {
        if (row == 0) {
            return 0b11111;
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
            return 0b00100;
        }
        if (row == 5) {
            return 0b00100;
        }
        return 0b00100;
    }
}
