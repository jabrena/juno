package io.github.jabrena.juno.api.led;

/** Row-bit tables for digits 5-9 (local index 0-4 = digit - 5), split out of {@link LedMatrixFont}. */
final class LedMatrixDigits5to9 {
    private LedMatrixDigits5to9() {
    }

    /** digit: local index 0-4 within this shard. row: 0 (top) to 6 (bottom). */
    static int rowBits(int digit, int row) {
        if (digit == 0) {
            return digit5RowBits(row);
        }
        if (digit == 1) {
            return digit6RowBits(row);
        }
        if (digit == 2) {
            return digit7RowBits(row);
        }
        if (digit == 3) {
            return digit8RowBits(row);
        }
        return digit9RowBits(row);
    }

    private static int digit5RowBits(int row) {
        if (row == 0) {
            return 0b11111;
        }
        if (row == 1) {
            return 0b10000;
        }
        if (row == 2) {
            return 0b11110;
        }
        if (row == 3) {
            return 0b00001;
        }
        if (row == 4) {
            return 0b00001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01110;
    }

    private static int digit6RowBits(int row) {
        if (row == 0) {
            return 0b00110;
        }
        if (row == 1) {
            return 0b01000;
        }
        if (row == 2) {
            return 0b10000;
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
        return 0b01110;
    }

    private static int digit7RowBits(int row) {
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
            return 0b01000;
        }
        return 0b01000;
    }

    private static int digit8RowBits(int row) {
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
            return 0b01110;
        }
        if (row == 4) {
            return 0b10001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01110;
    }

    private static int digit9RowBits(int row) {
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
            return 0b01111;
        }
        if (row == 4) {
            return 0b00001;
        }
        if (row == 5) {
            return 0b00010;
        }
        return 0b01100;
    }
}
