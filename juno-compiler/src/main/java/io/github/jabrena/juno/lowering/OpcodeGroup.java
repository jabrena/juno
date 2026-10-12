package io.github.jabrena.juno.lowering;

/** A family of related opcodes and how to lower each of them. */
interface OpcodeGroup {
    void registerOn(OpcodeTable table);
}
