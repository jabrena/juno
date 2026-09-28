package io.github.jabrena.juno.api.led;

/** Row-bit tables for uppercase letters A-G, split out of {@link LedMatrixFont} to keep per-class cyclomatic complexity down. */
final class LedMatrixLettersAtoG {
    private LedMatrixLettersAtoG() {
    }

    /** letter: local index within this shard (0 = first letter of its range). row: 0 (top) to 6 (bottom). */
    static int rowBits(int letter, int row) {
        if (letter == 0) {
            return letterARowBits(row);
        }
        if (letter == 1) {
            return letterBRowBits(row);
        }
        if (letter == 2) {
            return letterCRowBits(row);
        }
        if (letter == 3) {
            return letterDRowBits(row);
        }
        if (letter == 4) {
            return letterERowBits(row);
        }
        if (letter == 5) {
            return letterFRowBits(row);
        }
        return letterGRowBits(row);
    }

    private static int letterARowBits(int row) {
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
            return 0b11111;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }

    private static int letterBRowBits(int row) {
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
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b11110;
    }

    private static int letterCRowBits(int row) {
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
            return 0b10000;
        }
        if (row == 4) {
            return 0b10000;
        }
        if (row == 5) {
            return 0b10000;
        }
        return 0b01111;
    }

    private static int letterDRowBits(int row) {
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

    private static int letterERowBits(int row) {
        if (row == 0) {
            return 0b11111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10000;
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
        return 0b11111;
    }

    private static int letterFRowBits(int row) {
        if (row == 0) {
            return 0b11111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b10000;
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

    private static int letterGRowBits(int row) {
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
            return 0b10111;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01111;
    }
}
