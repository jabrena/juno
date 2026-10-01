package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.IrInstruction;

import java.util.List;

import static io.github.jabrena.juno.lowering.StackValueOps.pop;
import static io.github.jabrena.juno.lowering.StackValueOps.storeToStack;

/** Lowering for supported JDK bytecode idioms whose implementation classes stay outside the closed world. */
final class JdkInvokeLowering {
    private JdkInvokeLowering() {
    }

    static Lowered lowerRequireNonNull(List<IrInstruction> instructions, int stackBase, int depth,
                                       int nextValueId, ValueTracking tracking) {
        Popped reference = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = reference.nextValueId();
        instructions.add(new IrInstruction.NullCheck(reference.value()));
        storeToStack(instructions, stackBase, depth, reference.value(), tracking);
        return new Lowered(nextValueId, depth + 1);
    }
}
