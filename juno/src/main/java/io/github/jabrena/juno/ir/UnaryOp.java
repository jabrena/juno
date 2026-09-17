package io.github.jabrena.juno.ir;

/** A single-operand operation: arithmetic negation or a narrowing conversion. */
public enum UnaryOp {
    NEGATE,
    TO_BYTE,
    TO_CHAR,
    TO_SHORT
}
