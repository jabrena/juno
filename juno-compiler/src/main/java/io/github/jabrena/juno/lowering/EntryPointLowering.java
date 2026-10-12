package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.classfile.MethodRef;
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
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the generated entry functions the runtime shim starts threads and tasks with, from the closed-world
 * dispatch the linker resolved, so a lambda, a capturing lambda, or a class implementing the interface all start
 * the same way: {@code void entry(Runnable r) { r.run(); }} for a thread, and
 * {@code Object entry(Callable task) { return task.call(); }} for a task (and for {@code ScopedValue.CallableOp}
 * behind {@code Carrier.call}). An exception escaping the body is left pending for the shim to report.
 */
final class EntryPointLowering {
    private final Map<String, Integer> objectTypeIds;

    EntryPointLowering(Map<String, Integer> objectTypeIds) {
        this.objectTypeIds = objectTypeIds;
    }

    IrMethod threadEntry(InterfaceDispatch dispatch) {
        Value runnable = Value.int32(0);
        IrBasicBlock block = new IrBasicBlock(0, List.of(new IrInstruction.LoadLocal(runnable, 0),
                invoke(dispatch, Optional.empty(), runnable)), new IrTerminator.Return(Optional.empty()));
        return IrMethod.withInferredValues(ThreadSupport.ENTRY_METHOD, 1, 1, List.of(), List.of(block));
    }

    IrMethod taskEntry(InterfaceDispatch dispatch, MethodRef entryMethod) {
        Value callable = Value.int32(0);
        Value result = Value.int32(1);
        IrBasicBlock block = new IrBasicBlock(0, List.of(new IrInstruction.LoadLocal(callable, 0),
                invoke(dispatch, Optional.of(result), callable)), new IrTerminator.Return(Optional.of(result)));
        return IrMethod.withInferredValues(entryMethod, 1, 2, List.of(), List.of(block));
    }

    private IrInstruction invoke(InterfaceDispatch dispatch, Optional<Value> result, Value receiver) {
        if (dispatch.targets().size() == 1) {
            InterfaceDispatch.Target target = dispatch.targets().getFirst();
            return target.isLambda()
                    ? new IrInstruction.LambdaCall(result, Objects.requireNonNull(target.lambda()), List.of(receiver))
                    : new IrInstruction.Call(result, target.method(), List.of(receiver));
        }
        List<InterfaceTarget> targets = dispatch.targets().stream()
                .map(target -> new InterfaceTarget(Objects.requireNonNull(objectTypeIds.get(target.className())),
                        target.method(), target.lambda()))
                .toList();
        return new IrInstruction.InterfaceCall(result, List.of(receiver), targets);
    }
}
