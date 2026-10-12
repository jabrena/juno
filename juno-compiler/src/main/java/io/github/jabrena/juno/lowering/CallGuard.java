package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Cross-method exception propagation. A callee that throws leaves the exception in the runtime's pending slot
 * and returns; right after every call to such a method this guard polls the slot. If an exception is pending
 * it is routed to the covering {@code catch}/{@code finally} handler (clearing the slot), or, when no handler
 * matches, the frame returns too and the unwinding continues in the caller. Not C++ exceptions: just an
 * ordinary return value check, so it costs nothing in methods that never call a throwing method.
 *
 * <p>A decorator around the invoke lowering: it lowers the call, then splits the IR block after it if needed.
 */
final class CallGuard implements InstructionLowering {
    private final MethodContext context;
    private final InstructionLowering invoke;

    CallGuard(MethodContext context, InstructionLowering invoke) {
        this.context = context;
        this.invoke = invoke;
    }

    @Override
    public void lower(Instruction instruction) {
        IrBlockWriter code = context.code();
        int firstNew = code.size();
        invoke.lower(instruction);
        ThrowingMethods throwing = context.program().throwing();
        IntStream.range(firstNew, code.size())
                .filter(index -> throwing.mayThrow(code.get(index)))
                .max()
                .ifPresent(callIndex -> pollAfter(instruction, callIndex));
    }

    /** Ends the block after the call at {@code callIndex} with a pending-exception test. */
    private void pollAfter(Instruction instruction, int callIndex) {
        IrBlockWriter code = context.code();
        FreshValues values = context.values();
        List<IrInstruction> rest = code.takeFrom(callIndex + 1);
        Value pending = values.int32();
        code.emit(new IrInstruction.IntrinsicCall(Optional.of(pending), Intrinsic.THROW_PENDING, Optional.empty(),
                List.of(), List.of()));
        int continuation = IrBlockWriter.CALL_BLOCK_BASE + 2 * instruction.offset();
        ExceptionRouting.HandlerTable handlers = context.exceptions()
                .handlersAt(instruction.offset(), context.program().throwableClasses());
        if (handlers.isEmpty()) {
            code.split(new IrTerminator.Branch(pending, IrBlockWriter.PROPAGATE_BLOCK, continuation), continuation);
        } else {
            int dispatch = continuation + 1;
            code.split(new IrTerminator.Branch(pending, dispatch, continuation), continuation);
            Value mask = values.int32();
            Value classId = values.int32();
            code.add(new IrBasicBlock(dispatch, List.of(
                    new IrInstruction.StoreLocal(context.stack().bottomSlot(), pending),
                    new IrInstruction.Const(mask, handlers.caughtMask()),
                    new IrInstruction.IntrinsicCall(Optional.of(classId), Intrinsic.THROW_CATCH, Optional.empty(),
                            List.of(mask), List.of())),
                    handlers.dispatch(classId)));
        }
        code.emitAll(rest);
    }
}
