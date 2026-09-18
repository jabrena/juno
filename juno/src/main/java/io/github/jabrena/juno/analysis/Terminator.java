package io.github.jabrena.juno.analysis;

import java.util.List;

/** Where control flow goes after the last instruction of a {@link BasicBlock}. */
public sealed interface Terminator {
    /** Unconditional {@code goto}. */
    record Jump(int target) implements Terminator {}

    /** A conditional branch: control goes to {@code trueTarget} or falls through to {@code falseTarget}. */
    record Branch(int trueTarget, int falseTarget) implements Terminator {}

    /** A supported JVM return opcode, or the implicit return past the last instruction of a method. */
    record Return() implements Terminator {}

    /** Falls into the next block without an explicit branch instruction. */
    record Fallthrough(int target) implements Terminator {}

    record Switch(List<Integer> keys, List<Integer> targets, int defaultTarget) implements Terminator {
        public Switch {
            keys = List.copyOf(keys);
            targets = List.copyOf(targets);
        }
    }
}
