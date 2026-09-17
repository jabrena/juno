package io.github.jabrena.juno.ir;

import java.util.Optional;

/** Where control flow goes after the last instruction of an {@link IrBasicBlock}. */
public sealed interface IrTerminator {
    record Jump(int target) implements IrTerminator {
    }

    record Branch(Value condition, int trueTarget, int falseTarget) implements IrTerminator {
    }

    record Return(Optional<Value> value) implements IrTerminator {
    }
}
