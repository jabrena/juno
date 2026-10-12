package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Optional;

import static io.github.jabrena.juno.bytecode.Opcodes.*;

/** Branches, {@code goto}, switches, the return family and {@code athrow}: the instructions that end a block. */
final class ControlFlowLowering implements OpcodeGroup {
    private final MethodContext context;
    private final OperandStack stack;
    private final FreshValues values;

    ControlFlowLowering(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
        this.values = context.values();
    }

    @Override
    public void registerOn(OpcodeTable table) {
        table.on(IFEQ, IFLE, instruction -> terminate(compareWithZero(condition(instruction.opcode() - IFEQ))));
        table.on(IF_ICMPEQ, IF_ICMPLE,
                instruction -> terminate(compareTwo(condition(instruction.opcode() - IF_ICMPEQ))));
        // Every reference-shaped Juno value (an array handle, an enum ordinal) is an int, so this is int equality.
        table.on(IF_ACMPEQ, IF_ACMPNE,
                instruction -> terminate(compareTwo(condition(instruction.opcode() - IF_ACMPEQ))));
        table.on(IFNULL, IFNONNULL,
                instruction -> terminate(compareWithZero(condition(instruction.opcode() - IFNULL))));
        table.on(GOTO, instruction -> terminate(
                new IrTerminator.Jump(((Terminator.Jump) context.currentBlock().terminator()).target())));
        table.on(TABLESWITCH, LOOKUPSWITCH, instruction -> terminate(switchOn(stack.pop())));
        table.on(IRETURN, instruction -> terminate(returning(stack.pop())));
        table.on(ARETURN, instruction -> terminate(returning(stack.pop())));
        table.on(LRETURN, instruction -> terminate(returning(stack.popLong())));
        table.on(FRETURN, instruction -> terminate(returning(stack.popFloat())));
        table.on(DRETURN, instruction -> terminate(returning(stack.popDouble())));
        table.on(RETURN, instruction -> terminate(new IrTerminator.Return(Optional.empty())));
        table.on(ATHROW, instruction -> terminate(lowerThrow(instruction, stack.pop())));
    }

    private void terminate(IrTerminator terminator) {
        context.code().terminate(terminator);
    }

    private IrTerminator switchOn(Value selector) {
        Terminator.Switch switched = (Terminator.Switch) context.currentBlock().terminator();
        return new IrTerminator.Switch(selector, switched.keys(), switched.targets(), switched.defaultTarget());
    }

    private IrTerminator compareWithZero(Condition condition) {
        Value operand = stack.pop();
        Value zero = values.int32();
        context.emit(new IrInstruction.Const(zero, 0));
        return branch(condition, operand, zero);
    }

    private IrTerminator compareTwo(Condition condition) {
        Value right = stack.pop();
        Value left = stack.pop();
        return branch(condition, left, right);
    }

    private IrTerminator branch(Condition condition, Value left, Value right) {
        Value test = values.int32();
        context.emit(new IrInstruction.Compare(test, condition, left, right));
        Terminator.Branch branch = (Terminator.Branch) context.currentBlock().terminator();
        return new IrTerminator.Branch(test, branch.trueTarget(), branch.falseTarget());
    }

    private IrTerminator returning(Value value) {
        return new IrTerminator.Return(Optional.of(value));
    }

    /** The six comparisons appear in the same order in every branch family, from "equal" to "less or equal". */
    private Condition condition(int index) {
        return switch (index) {
            case 0 -> Condition.EQUAL;
            case 1 -> Condition.NOT_EQUAL;
            case 2 -> Condition.LESS_THAN;
            case 3 -> Condition.GREATER_EQUAL;
            case 4 -> Condition.GREATER_THAN;
            case 5 -> Condition.LESS_EQUAL;
            default -> throw new IllegalStateException("Unexpected comparison index " + index);
        };
    }

    /**
     * {@code athrow}: resolves, at compile time, which handler of this method (in exception-table order) catches
     * each of the program's throwable classes. At runtime the thrown object's class id picks the handler, which
     * starts with the exception as its only operand-stack value; a class no handler here catches keeps unwinding.
     */
    private IrTerminator lowerThrow(Instruction instruction, Value thrown) {
        ExceptionRouting.HandlerTable handlers = context.exceptions()
                .handlersAt(instruction.offset(), context.program().throwableClasses());
        if (handlers.isEmpty()) {
            context.emit(new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.THROW_RAISE, Optional.empty(),
                    List.of(thrown), List.of()));
            return new IrTerminator.Return(Optional.empty());
        }
        stack.storeAsOnlyValue(thrown);
        Value mask = values.int32();
        context.emit(new IrInstruction.Const(mask, handlers.caughtMask()));
        Value classId = values.int32();
        context.emit(new IrInstruction.IntrinsicCall(Optional.of(classId), Intrinsic.THROW_DISPATCH,
                Optional.empty(), List.of(thrown, mask), List.of()));
        return handlers.dispatch(classId);
    }
}
