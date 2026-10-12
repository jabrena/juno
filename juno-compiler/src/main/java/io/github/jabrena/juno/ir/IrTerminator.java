package io.github.jabrena.juno.ir;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Where control flow goes after the last instruction of an {@link IrBasicBlock}. */
public sealed interface IrTerminator {
    /** Every value this terminator reads. */
    default List<Value> referencedValues() {
        return switch (this) {
            case IrTerminator.Jump ignored -> List.of();
            case IrTerminator.Branch branch -> List.of(branch.condition());
            case IrTerminator.Return returned -> returned.value().stream().toList();
            case IrTerminator.Switch switched -> List.of(switched.selector());
        };
    }

    /** Where control may go next, in the order the targets are listed. */
    default List<Integer> successors() {
        return switch (this) {
            case IrTerminator.Jump jump -> List.of(jump.target());
            case IrTerminator.Branch branch -> List.of(branch.trueTarget(), branch.falseTarget());
            case IrTerminator.Return ignored -> List.of();
            case IrTerminator.Switch switched -> Stream.concat(switched.targets().stream(),
                    Stream.of(switched.defaultTarget())).toList();
        };
    }

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
