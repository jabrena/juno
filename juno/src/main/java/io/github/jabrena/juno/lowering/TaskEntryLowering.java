package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.InterfaceTarget;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.InterfaceDispatch;
import io.github.jabrena.juno.linker.StructuredTaskSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds {@code Object entry(Callable task) { return task.call(); }} for the runtime task bootstrap. */
final class TaskEntryLowering {
    private TaskEntryLowering() {
    }

    static IrMethod lower(InterfaceDispatch dispatch, Map<String, Integer> objectTypeIds) {
        Value callable = Value.int32(0);
        Value result = Value.int32(1);
        List<IrInstruction> instructions = new ArrayList<>();
        instructions.add(new IrInstruction.LoadLocal(callable, 0));
        if (dispatch.targets().size() == 1) {
            InterfaceDispatch.Target target = dispatch.targets().getFirst();
            instructions.add(target.isLambda()
                    ? new IrInstruction.LambdaCall(Optional.of(result), target.lambda(), List.of(callable))
                    : new IrInstruction.Call(Optional.of(result), target.method(), List.of(callable)));
        } else {
            List<InterfaceTarget> targets = dispatch.targets().stream()
                    .map(target -> new InterfaceTarget(objectTypeIds.get(target.className()), target.method(),
                            target.lambda()))
                    .toList();
            instructions.add(new IrInstruction.InterfaceCall(Optional.of(result), List.of(callable), targets));
        }
        IrBasicBlock block = new IrBasicBlock(0, List.copyOf(instructions),
                new IrTerminator.Return(Optional.of(result)));
        return IrMethod.withInferredValues(StructuredTaskSupport.ENTRY_METHOD, 1, 2, List.of(), List.of(block));
    }
}
