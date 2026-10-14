package io.github.jabrena.juno.backend.h8300;

/** The conditions of the H8/300 {@code Bcc d:8} branches, each with its opcode byte. */
public enum H8Condition {
    ALWAYS(0x40),
    NEVER(0x41),
    HIGHER(0x42),
    LOWER_OR_SAME(0x43),
    CARRY_CLEAR(0x44),
    CARRY_SET(0x45),
    NOT_EQUAL(0x46),
    EQUAL(0x47),
    OVERFLOW_CLEAR(0x48),
    OVERFLOW_SET(0x49),
    PLUS(0x4A),
    MINUS(0x4B),
    GREATER_OR_EQUAL(0x4C),
    LESS(0x4D),
    GREATER(0x4E),
    LESS_OR_EQUAL(0x4F);

    private final int opcode;

    H8Condition(int opcode) {
        this.opcode = opcode;
    }

    /** The condition that holds exactly when this one does not: the opcodes come in pairs differing in bit 0. */
    public H8Condition opposite() {
        return values()[ordinal() ^ 1];
    }

    int opcode() {
        return opcode;
    }
}
