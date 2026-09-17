package io.github.jabrena.juno.ir;

/** A symbolic virtual register: one value, produced by exactly one {@link IrInstruction}. */
public record Value(int id) {
}
