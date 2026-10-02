package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.StructuredTaskSupport;

import java.util.List;
import java.util.Optional;

import static io.github.jabrena.juno.lowering.StackValueOps.pop;
import static io.github.jabrena.juno.lowering.StackValueOps.storeToStack;

/** Bytecode-stack adaptations that are specific to structured-task policy objects. */
final class StructuredTaskLowering {
    private StructuredTaskLowering() {
    }

    /** Replaces the uninitialized policy-scope placeholder with the task runtime's arena handle. */
    static Lowered lowerConstruction(MethodRef called, List<IrInstruction> instructions, int stackBase,
                                     int depth, int nextValueId, ValueTracking tracking) {
        Popped discardedReceiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = discardedReceiver.nextValueId();
        Value handle = Value.int32(nextValueId++);
        Intrinsic intrinsic = called.owner().equals(StructuredTaskSupport.SHUTDOWN_ON_FAILURE)
                ? Intrinsic.TASK_SCOPE_NEW_FAILURE : Intrinsic.TASK_SCOPE_NEW_SUCCESS;
        instructions.add(new IrInstruction.IntrinsicCall(Optional.of(handle), intrinsic,
                Optional.empty(), List.of(), List.of()));
        storeToStack(instructions, stackBase, depth - 1, handle, tracking);
        return new Lowered(nextValueId, depth);
    }

    static Lowered lowerInterface(LinkedMethod linked, Instruction instruction, MethodRef called,
                                  List<IrInstruction> instructions, int stackBase, int depth,
                                  int nextValueId, ValueTracking tracking) {
        return InvokeLowering.lowerResolvedCall(linked, instruction, called, true, List.of(), instructions,
                stackBase, depth, nextValueId, tracking);
    }
}
