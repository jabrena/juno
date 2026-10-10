package io.github.jabrena.juno.ir;

import java.util.Optional;
import java.util.List;

/** Where control flow goes after the last instruction of an {@link IrBasicBlock}. */
public sealed interface IrTerminator {
    record Jump(int target) implements IrTerminator {
    }

    record Branch(Value condition, int trueTarget, int falseTarget) implements IrTerminator {
    }

    record Return(Optional<Value> value) implements IrTerminator {
    }

    record Switch(Value selector, List<Integer> keys, List<Integer> targets, int defaultTarget)
            implements IrTerminator {
        public Switch {
            keys = List.copyOf(keys);
            targets = List.copyOf(targets);
            if (keys.size() != targets.size()) {
                throw new IllegalArgumentException("Switch keys and targets must have equal size");
            }
        }
    }
}
