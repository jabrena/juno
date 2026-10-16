package io.github.jabrena.juno.backend.h8300;

/**
 * The sixteen 8-bit halves of the general registers: {@code rNh} is the high byte of {@code rN} and {@code rNl} its
 * low byte, numbered in instructions as r0h-r7h = 0-7 and r0l-r7l = 8-15.
 */
public enum H8ByteRegister {
    R0H(0), R1H(1), R2H(2), R3H(3), R4H(4), R5H(5), R6H(6), R7H(7),
    R0L(8), R1L(9), R2L(10), R3L(11), R4L(12), R5L(13), R6L(14), R7L(15);

    private final int number;

    H8ByteRegister(int number) {
        this.number = number;
    }

    /** The low byte of {@code register}. */
    public static H8ByteRegister low(H8Register register) {
        return switch (register) {
            case R0 -> R0L;
            case R1 -> R1L;
            case R2 -> R2L;
            case R3 -> R3L;
            case R4 -> R4L;
            case R5 -> R5L;
            case R6 -> R6L;
            case R7 -> R7L;
        };
    }

    /** The high byte of {@code register}. */
    public static H8ByteRegister high(H8Register register) {
        return switch (register) {
            case R0 -> R0H;
            case R1 -> R1H;
            case R2 -> R2H;
            case R3 -> R3H;
            case R4 -> R4H;
            case R5 -> R5H;
            case R6 -> R6H;
            case R7 -> R7H;
        };
    }

    /** The register's number in an instruction's register fields. */
    int number() {
        return number;
    }
}
