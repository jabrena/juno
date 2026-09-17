package io.github.jabrena.juno.ir;

/** The comparison an {@link IrInstruction.Compare} evaluates between its two operands. */
public enum Condition {
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    GREATER_EQUAL,
    GREATER_THAN,
    LESS_EQUAL
}
