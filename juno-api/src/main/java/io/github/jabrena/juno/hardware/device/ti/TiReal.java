package io.github.jabrena.juno.hardware.device.ti;

/**
 * Writes TI reals: 9 bytes, a sign byte (0x80 negative), the decimal exponent biased by 0x80, and fourteen BCD digits
 * of mantissa, the first one non-zero (zero is all-zero digits with exponent 0x80). Allocation-free: it reuses its
 * digit scratch buffer. Fourteen digits hold a {@code YYYYMMDDHHMMSS} time stamp exactly.
 */
final class TiReal {
    static final int BYTES = 9;
    private static final int DIGITS = 14;

    private final byte[] digits = new byte[20];

    /** Writes {@code value / 10^decimals} at {@code target[at]}. */
    void put(byte[] target, int at, long value, int decimals) {
        for (int i = 0; i < BYTES; i++) {
            target[at + i] = 0;
        }
        target[at + 1] = (byte) 0x80;
        long magnitude = value < 0 ? -value : value;
        if (magnitude == 0) {
            return;
        }
        target[at] = (byte) (value < 0 ? 0x80 : 0x00);
        int count = 0;
        while (magnitude > 0) {
            digits[count++] = (byte) (magnitude % 10);
            magnitude /= 10;
        }
        target[at + 1] = (byte) (0x80 + count - 1 - decimals);
        int digit = 0;
        for (int i = count - 1; i >= 0 && digit < DIGITS; i--) {
            int index = at + 2 + digit / 2;
            target[index] = (byte) (digit % 2 == 0 ? digits[i] << 4 : target[index] | digits[i]);
            digit++;
        }
    }
}
