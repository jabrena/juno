package io.github.jabrena.juno.api.led;

/** Row-bit tables for digits 0-4, split out of {@link LedMatrixFont} to keep per-class cyclomatic complexity down. */
final class LedMatrixDigits0to4 {
    private LedMatrixDigits0to4() {
    }

    /** digit: local index 0-4 within this shard. row: 0 (top) to 6 (bottom). */
    static int rowBits(int digit, int row) {
        if (digit == 0) {
            return digit0RowBits(row);
        }
        if (digit == 1) {
            return digit1RowBits(row);
        }
        if (digit == 2) {
            return digit2RowBits(row);
        }
        if (digit == 3) {
            return digit3RowBits(row);
        }
        return digit4RowBits(row);
    }

    private static int digit0RowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b10011;
        }
        if (row == 3) {
            return 0b10101;
        }
        if (row == 4) {
            return 0b11001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01110;
    }

    private static int digit1RowBits(int row) {
        if (row == 0) {
            return 0b00100;
        }
        if (row == 1) {
            return 0b01100;
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

    private static int digit2RowBits(int row) {
        if (row == 0) {
            return 0b01110;
        }
        if (row == 1) {
            return 0b10001;
        }
        if (row == 2) {
            return 0b00001;
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
        return 0b11111;
    }

    private static int digit3RowBits(int row) {
        if (row == 0) {
            return 0b11111;
        }
        if (row == 1) {
            return 0b00010;
        }
        if (row == 2) {
            return 0b00100;
        }
        if (row == 3) {
            return 0b00010;
        }
        if (row == 4) {
            return 0b00001;
        }
        if (row == 5) {
            return 0b10001;
        }
        return 0b01110;
    }

    private static int digit4RowBits(int row) {
        if (row == 0) {
            return 0b00010;
        }
        if (row == 1) {
            return 0b00110;
        }
        if (row == 2) {
            return 0b01010;
        }
        if (row == 3) {
            return 0b10010;
        }
        if (row == 4) {
            return 0b11111;
        }
        if (row == 5) {
            return 0b00010;
        }
        return 0b00010;
    }
}
