package io.github.jabrena.juno.backend.h8300;

/** The eight 16-bit general registers of the H8/300; {@link #R7} is the stack pointer. */
public enum H8Register {
    R0, R1, R2, R3, R4, R5, R6, R7;

    /** The stack pointer, which {@code push}/{@code pop} and {@code jsr}/{@code rts} use. */
    public static final H8Register SP = R7;

    /** The register's number in an instruction's register fields. */
    int number() {
        return ordinal();
    }
}
