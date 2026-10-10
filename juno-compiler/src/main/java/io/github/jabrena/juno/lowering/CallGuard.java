package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.ControlFlowLowering.CALL_BLOCK_BASE;
import static io.github.jabrena.juno.lowering.ControlFlowLowering.PROPAGATE_BLOCK;
import static io.github.jabrena.juno.lowering.ControlFlowLowering.caughtMask;
import static io.github.jabrena.juno.lowering.ControlFlowLowering.dispatchTerminator;
import static io.github.jabrena.juno.lowering.ControlFlowLowering.handlersByClassId;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cross-method exception propagation. A callee that throws leaves the exception in the runtime's pending slot
 * and returns; right after every call to such a method this guard polls the slot. If an exception is pending
 * it is routed to the covering {@code catch}/{@code finally} handler (clearing the slot), or, when no handler
 * matches, the frame returns too and the unwinding continues in the caller. Not C++ exceptions: just an
 * ordinary return value check, so it costs nothing in methods that never call a throwing method.
 */
final class CallGuard {
    private CallGuard() {
    }

    /**
     * Splits the IR block after the throwing call lowered into {@code instructions[firstNew..]}, if any.
     * {@code irBlockStart} is the id of the block being built; the returned lowering carries the id of the
     * continuation block that the rest of the bytecode instruction (and everything after it) goes into.
     */
    static InstructionLowering guard(InstructionLowering lowered, int firstNew, LinkedMethod linked,
                                     Instruction instruction, List<IrBasicBlock> blocks,
                                     List<IrInstruction> instructions, int stackBase, int irBlockStart,
                                     Set<MethodRef> throwing, Map<String, JavaClass> classes,
                                     List<String> throwableClasses) {
        int callIndex = -1;
        for (int index = firstNew; index < instructions.size(); index++) {
            if (ThrowingMethods.raises(instructions.get(index))
                    || ThrowingMethods.callees(instructions.get(index)).stream().anyMatch(throwing::contains)) {
                callIndex = index;
            }
        }
        if (callIndex < 0) {
            return lowered;
        }
        int nextValueId = lowered.nextValueId();
        List<IrInstruction> head = new ArrayList<>(instructions.subList(0, callIndex + 1));
        List<IrInstruction> tail = new ArrayList<>(instructions.subList(callIndex + 1, instructions.size()));
        instructions.clear();
        instructions.addAll(tail);

        Value pending = Value.int32(nextValueId++);
        head.add(new IrInstruction.IntrinsicCall(Optional.of(pending), Intrinsic.THROW_PENDING, Optional.empty(),
                List.of(), List.of()));
        int continuation = CALL_BLOCK_BASE + 2 * instruction.offset();
        Map<Integer, Integer> handlers = handlersByClassId(linked, instruction.offset(), classes, throwableClasses);
        if (handlers.isEmpty()) {
            blocks.add(new IrBasicBlock(irBlockStart, head,
                    new IrTerminator.Branch(pending, PROPAGATE_BLOCK, continuation)));
            return new InstructionLowering(nextValueId, lowered.depth(), continuation, null);
        }
        int dispatch = continuation + 1;
        blocks.add(new IrBasicBlock(irBlockStart, head, new IrTerminator.Branch(pending, dispatch, continuation)));
        Value mask = Value.int32(nextValueId++);
        Value classId = Value.int32(nextValueId++);
        blocks.add(new IrBasicBlock(dispatch, List.of(
                new IrInstruction.StoreLocal(stackBase, pending),
                new IrInstruction.Const(mask, caughtMask(handlers)),
                new IrInstruction.IntrinsicCall(Optional.of(classId), Intrinsic.THROW_CATCH, Optional.empty(),
                        List.of(mask), List.of())),
                dispatchTerminator(handlers, throwableClasses.size(), classId)));
        return new InstructionLowering(nextValueId, lowered.depth(), continuation, null);
    }

    /** The propagation block every unmatched exception funnels into, if some terminator targets it. */
    static Optional<IrBasicBlock> propagateBlock(List<IrBasicBlock> blocks) {
        boolean referenced = blocks.stream().anyMatch(block -> switch (block.terminator()) {
            case IrTerminator.Branch branch -> branch.trueTarget() == PROPAGATE_BLOCK
                    || branch.falseTarget() == PROPAGATE_BLOCK;
            case IrTerminator.Switch switched -> switched.defaultTarget() == PROPAGATE_BLOCK
                    || switched.targets().contains(PROPAGATE_BLOCK);
            case IrTerminator.Jump jump -> jump.target() == PROPAGATE_BLOCK;
            case IrTerminator.Return ignored -> false;
        });
        return referenced
                ? Optional.of(new IrBasicBlock(PROPAGATE_BLOCK, List.of(), new IrTerminator.Return(Optional.empty())))
                : Optional.empty();
    }
}
