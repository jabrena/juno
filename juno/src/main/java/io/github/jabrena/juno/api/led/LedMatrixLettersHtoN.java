package io.github.jabrena.juno.api.led;

/** Row-bit tables for uppercase letters H-N, split out of {@link LedMatrixFont} to keep per-class cyclomatic complexity down. */
final class LedMatrixLettersHtoN {
    private LedMatrixLettersHtoN() {
    }

    /** letter: local index within this shard (0 = first letter of its range). row: 0 (top) to 6 (bottom). */
    static int rowBits(int letter, int row) {
        if (letter == 0) {
            return letterHRowBits(row);
        }
        if (letter == 1) {
            return letterIRowBits(row);
        }
        if (letter == 2) {
            return letterJRowBits(row);
        }
        if (letter == 3) {
            return letterKRowBits(row);
        }
        if (letter == 4) {
            return letterLRowBits(row);
        }
        if (letter == 5) {
            return letterMRowBits(row);
        }
        return letterNRowBits(row);
    }

    private static int letterHRowBits(int row) {
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

    private static int letterIRowBits(int row) {
        if (row == 0) {
            return 0b01110;
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
        return 0b01110;
    }

    private static int letterJRowBits(int row) {
        if (row == 0) {
            return 0b00111;
        }
        if (row == 1) {
            return 0b00010;
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
            return 0b10010;
        }
        return 0b01100;
    }

    private static int letterKRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b10010;
        }
        if (row == 2) {
            return 0b10100;
        }
        if (row == 3) {
            return 0b11000;
        }
        if (row == 4) {
            return 0b10100;
        }
        if (row == 5) {
            return 0b10010;
        }
        return 0b10001;
    }

    private static int letterLRowBits(int row) {
        if (row == 0) {
            return 0b10000;
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
        return 0b11111;
    }

    private static int letterMRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b11011;
        }
        if (row == 2) {
            return 0b10101;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }

    private static int letterNRowBits(int row) {
        if (row == 0) {
            return 0b10001;
        }
        if (row == 1) {
            return 0b11001;
        }
        if (row == 2) {
            return 0b10101;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b10011;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b10001;
    }
}
