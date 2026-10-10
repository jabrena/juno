package io.github.jabrena.juno.ir;

/** A binary arithmetic or bitwise operation, with Java {@code int} (32-bit two's complement) semantics. */
public enum BinaryOp {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REMAINDER,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    UNSIGNED_SHIFT_RIGHT,
    AND,
    OR,
    XOR
}
