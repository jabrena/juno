package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.InterfaceTarget;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.InterfaceDispatch;
import io.github.jabrena.juno.linker.ThreadSupport;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the generated thread entry function {@code void entry(Runnable r) { r.run(); }} from the
 * closed-world {@code Runnable.run()} dispatch the linker resolved. The runtime shim calls it on the new
 * thread's own stack, so a lambda, a capturing lambda, or a class implementing {@code Runnable} all start the
 * same way, and an exception escaping {@code run()} is left pending for the shim to report.
 */
final class ThreadEntryLowering {
    private ThreadEntryLowering() {
    }

    static IrMethod lower(InterfaceDispatch dispatch, Map<String, Integer> objectTypeIds) {
        Value runnable = Value.int32(0);
        List<IrInstruction> instructions = new java.util.ArrayList<>();
        instructions.add(new IrInstruction.LoadLocal(runnable, 0));
        if (dispatch.targets().size() == 1) {
            InterfaceDispatch.Target target = dispatch.targets().getFirst();
            instructions.add(target.isLambda()
                    ? new IrInstruction.LambdaCall(Optional.empty(), target.lambda(), List.of(runnable))
                    : new IrInstruction.Call(Optional.empty(), target.method(), List.of(runnable)));
        } else {
            List<InterfaceTarget> targets = dispatch.targets().stream()
                    .map(target -> new InterfaceTarget(objectTypeIds.get(target.className()), target.method(),
                            target.lambda()))
                    .toList();
            instructions.add(new IrInstruction.InterfaceCall(Optional.empty(), List.of(runnable), targets));
        }
        IrBasicBlock block = new IrBasicBlock(0, List.copyOf(instructions), new IrTerminator.Return(Optional.empty()));
        return IrMethod.withInferredValues(ThreadSupport.ENTRY_METHOD, 1, 1, List.of(), List.of(block));
    }
}
