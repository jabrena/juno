package io.github.jabrena.juno.backend.h8300;

/**
 * The sixteen 8-bit halves of the general registers: {@code rNh} is the high byte of {@code rN} and {@code rNl} its
 * low byte, numbered in instructions as r0h-r7h = 0-7 and r0l-r7l = 8-15.
 */
public enum H8ByteRegister {
    R0H, R1H, R2H, R3H, R4H, R5H, R6H, R7H,
    R0L, R1L, R2L, R3L, R4L, R5L, R6L, R7L;

    /** The low byte of {@code register}. */
    public static H8ByteRegister low(H8Register register) {
        return values()[8 + register.number()];
    }

    /** The high byte of {@code register}. */
    public static H8ByteRegister high(H8Register register) {
        return values()[register.number()];
    }

    /** The register's number in an instruction's register fields. */
    int number() {
        return ordinal();
    }
}
