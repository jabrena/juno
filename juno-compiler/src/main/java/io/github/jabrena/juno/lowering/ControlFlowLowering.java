package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.BytecodeToIr.*;
import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ExceptionHandler;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.ThrowableTypes;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import org.jspecify.annotations.Nullable;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Branch, return, {@code athrow}, and division-guard opcode lowering, split out of {@link BytecodeToIr}. */
final class ControlFlowLowering {
    private ControlFlowLowering() {
    }
    static final BitSet CONDITIONAL_BRANCH_OPCODES = bitSetOf(
            153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 198, 199);    static final int SYNTHETIC_BLOCK_BASE = 0x10000;    /** Continuation/dispatch blocks after a call that may throw (see {@link CallGuard}). */
    static final int CALL_BLOCK_BASE = 0x40000;    /** The shared block that returns from a frame with the pending exception still set. */
    static final int PROPAGATE_BLOCK = 0x7FFFFFF0;    static final String ARITHMETIC_EXCEPTION = "java/lang/ArithmeticException";    static ControlLowered lowerControlFlow(LinkedMethod linked, Instruction instruction, int opcode,
                                            BasicBlock block, List<IrInstruction> instructions, int stackBase,
                                            int depth, int nextValueId, ValueTracking tracking,
                                            Map<String, JavaClass> classes, List<String> throwableClasses) {
        if (CONDITIONAL_BRANCH_OPCODES.get(opcode)) {
            return lowerConditionalBranch(instruction, opcode, block, instructions, stackBase, depth, nextValueId,
                    tracking);
        }
        return lowerJumpOrReturn(linked, instruction, opcode, block, instructions, stackBase, depth, nextValueId,
                tracking, classes, throwableClasses);
    }    static ControlLowered lowerConditionalBranch(Instruction instruction, int opcode, BasicBlock block,
                                                  List<IrInstruction> instructions, int stackBase, int depth,
                                                  int nextValueId, ValueTracking tracking) {
        switch (opcode) {

                    case 153, 154, 155, 156, 157, 158 -> {
                        Popped operand = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = operand.nextValueId();
                        Value zero = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Const(zero, 0));
                        Value condition = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Compare(condition, conditionOf(opcode, 153), operand.value(), zero));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        IrTerminator terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                        return new ControlLowered(nextValueId, depth, terminator);
                    }

                    case 159, 160, 161, 162, 163, 164 -> {
                        Popped right = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = right.nextValueId();
                        Popped left = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = left.nextValueId();
                        Value condition = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Compare(
                                condition, conditionOf(opcode, 159), left.value(), right.value()));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        IrTerminator terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                        return new ControlLowered(nextValueId, depth, terminator);
                    }

                    case 165, 166 -> {
                        // Reference equality; every reference-shaped Juno value (an array handle, an enum
                        // constant's ordinal) is represented as a plain int32_t, so this is just int equality.
                        Popped right = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = right.nextValueId();
                        Popped left = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = left.nextValueId();
                        Value condition = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Compare(
                                condition, conditionOf(opcode, 165), left.value(), right.value()));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        IrTerminator terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                        return new ControlLowered(nextValueId, depth, terminator);
                    }

                    case 198, 199 -> {
                        Popped reference = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = reference.nextValueId();
                        Value nullValue = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Const(nullValue, 0));
                        Value condition = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Compare(
                                condition, conditionOf(opcode, 198), reference.value(), nullValue));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        IrTerminator terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                        return new ControlLowered(nextValueId, depth, terminator);
                    }
            default -> throw new IllegalStateException("unreachable conditional-branch opcode " + opcode);
        }
    }    static ControlLowered lowerJumpOrReturn(LinkedMethod linked, Instruction instruction, int opcode,
                                             BasicBlock block, List<IrInstruction> instructions, int stackBase,
                                             int depth, int nextValueId, ValueTracking tracking,
                                             Map<String, JavaClass> classes, List<String> throwableClasses) {
        IrTerminator terminator = null;
        switch (opcode) {
                    case 167 -> terminator = new IrTerminator.Jump(((Terminator.Jump) block.terminator()).target());

                    case 170, 171 -> {
                        Popped selector = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = selector.nextValueId();
                        Terminator.Switch switched = (Terminator.Switch) block.terminator();
                        terminator = new IrTerminator.Switch(selector.value(), switched.keys(), switched.targets(),
                                switched.defaultTarget());
                    }

                    case 172 -> {
                        Popped returned = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = returned.nextValueId();
                        terminator = new IrTerminator.Return(Optional.of(returned.value()));
                    }

                    case 173 -> {
                        depth -= 2;
                        WidePopped returned = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = returned.nextValueId();
                        Value packed = Value.int64(nextValueId++);
                        instructions.add(new IrInstruction.PackLong(packed, returned.low(), returned.high()));
                        terminator = new IrTerminator.Return(Optional.of(packed));
                    }

                    case 174 -> {
                        Popped returned = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = returned.nextValueId();
                        terminator = new IrTerminator.Return(Optional.of(returned.value()));
                    }

                    case 175 -> {
                        depth -= 2;
                        Popped returned = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = returned.nextValueId();
                        terminator = new IrTerminator.Return(Optional.of(returned.value()));
                    }

                    case 176 -> {
                        Popped returned = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = returned.nextValueId();
                        terminator = new IrTerminator.Return(Optional.of(returned.value()));
                    }

                    case 177 -> terminator = new IrTerminator.Return(Optional.empty());

                    case 191 -> {
                        Popped thrown = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = thrown.nextValueId();
                        Thrown lowered = lowerThrow(linked, instruction, block, thrown.value(), instructions,
                                stackBase, nextValueId, tracking, classes, throwableClasses);
                        nextValueId = lowered.nextValueId();
                        terminator = lowered.terminator();
                    }
            default -> throw new IllegalStateException("unreachable jump/return opcode " + opcode);
        }
        return new ControlLowered(nextValueId, depth, terminator);
    }    static Condition conditionOf(int opcode, int base) {
        return switch (opcode - base) {
            case 0 -> Condition.EQUAL;
            case 1 -> Condition.NOT_EQUAL;
            case 2 -> Condition.LESS_THAN;
            case 3 -> Condition.GREATER_EQUAL;
            case 4 -> Condition.GREATER_THAN;
            case 5 -> Condition.LESS_EQUAL;
            default -> throw new IllegalStateException("Unexpected comparison opcode " + opcode);
        };
    }    static @Nullable Integer arithmeticHandler(LinkedMethod linked, int offset, Map<String, JavaClass> classes) {
        for (ExceptionHandler handler : linked.method().exceptionHandlers()) {
            if (handler.covers(offset) && (handler.catchesAny()
                    || ThrowableTypes.isSubtype(ARITHMETIC_EXCEPTION, handler.catchType(), classes))) {
                return handler.handlerPc();
            }
        }
        return null;
    }    static boolean isIntegerDivision(int opcode) {
        return opcode == 108 || opcode == 109 || opcode == 112 || opcode == 113;
    }    static DivisorGuard guardZeroDivisor(LinkedMethod linked, Instruction instruction, boolean wide, int blockStart,
                                          List<IrInstruction> instructions, List<IrBasicBlock> blocks, int stackBase,
                                          int depth, int nextValueId, Map<String, JavaClass> classes,
                                          ValueTracking tracking) {
        Integer handler = arithmeticHandler(linked, instruction.offset(), classes);
        if (handler == null && !tracking.divisionByZeroUnwinds()) {
            return new DivisorGuard(nextValueId, blockStart);
        }
        Value divisor;
        if (wide) {
            Value low = Value.int32(nextValueId++);
            Value high = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.LoadLocal(low, stackBase + depth - 2));
            instructions.add(new IrInstruction.LoadLocal(high, stackBase + depth - 1));
            divisor = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.Binary(divisor, BinaryOp.OR, low, high));
        } else {
            divisor = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.LoadLocal(divisor, stackBase + depth - 1));
        }
        Value zero = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.Const(zero, 0));
        Value isZero = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.Compare(isZero, Condition.EQUAL, divisor, zero));
        int continuation = SYNTHETIC_BLOCK_BASE + 2 * instruction.offset();
        int thrower = continuation + 1;
        blocks.add(new IrBasicBlock(blockStart, List.copyOf(instructions),
                new IrTerminator.Branch(isZero, thrower, continuation)));
        instructions.clear();

        Value exception = Value.int32(nextValueId++);
        Value message = Value.int32(nextValueId++);
        blocks.add(new IrBasicBlock(thrower, List.of(
                new IrInstruction.NewObject(exception, ARITHMETIC_EXCEPTION),
                new IrInstruction.StringConst(message, "/ by zero"),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.THROWABLE_SET_MESSAGE,
                        Optional.of(exception), List.of(message), List.of()),
                handler == null
                        ? new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.THROW_RAISE, Optional.empty(),
                                List.of(exception), List.of())
                        : new IrInstruction.StoreLocal(stackBase, exception)),
                new IrTerminator.Jump(handler == null ? PROPAGATE_BLOCK : handler)));
        return new DivisorGuard(nextValueId, continuation);
    }    static Map<Integer, Integer> handlersByClassId(LinkedMethod linked, int offset,
                                                    Map<String, JavaClass> classes, List<String> throwableClasses) {
        Map<Integer, Integer> handlerByClassId = new TreeMap<>();
        for (int classId = 0; classId < throwableClasses.size(); classId++) {
            for (ExceptionHandler handler : linked.method().exceptionHandlers()) {
                if (handler.covers(offset) && (handler.catchesAny()
                        || ThrowableTypes.isSubtype(throwableClasses.get(classId), handler.catchType(), classes))) {
                    handlerByClassId.put(classId, handler.handlerPc());
                    break;
                }
            }
        }
        return handlerByClassId;
    }

    /** Value of the exception-class mask passed to {@code THROW_DISPATCH}/{@code THROW_CATCH}. */
    static int caughtMask(Map<Integer, Integer> handlerByClassId) {
        int caughtMask = 0;
        for (int classId : handlerByClassId.keySet()) {
            caughtMask |= 1 << classId;
        }
        return caughtMask;
    }

    /**
     * Routes a caught exception's class id to its handler. A class no handler matches keeps unwinding: the
     * default edge goes to the shared {@link #PROPAGATE_BLOCK}, unless every thrown class is caught.
     */
    static IrTerminator dispatchTerminator(Map<Integer, Integer> handlerByClassId, int classCount, Value classId) {
        List<Integer> keys = List.copyOf(handlerByClassId.keySet());
        List<Integer> targets = keys.stream().map(handlerByClassId::get).toList();
        if (handlerByClassId.size() == classCount) {
            return Set.copyOf(targets).size() == 1
                    ? new IrTerminator.Jump(targets.get(0))
                    : new IrTerminator.Switch(classId, keys, targets, targets.get(0));
        }
        return new IrTerminator.Switch(classId, keys, targets, PROPAGATE_BLOCK);
    }

    static Thrown lowerThrow(LinkedMethod linked, Instruction instruction, BasicBlock block, Value thrown,
                              List<IrInstruction> instructions, int stackBase, int nextValueId,
                              ValueTracking tracking, Map<String, JavaClass> classes, List<String> throwableClasses) {
        Map<Integer, Integer> handlerByClassId = handlersByClassId(linked, instruction.offset(), classes,
                throwableClasses);
        if (handlerByClassId.isEmpty()) {
            instructions.add(new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.THROW_RAISE,
                    Optional.empty(), List.of(thrown), List.of()));
            return new Thrown(new IrTerminator.Return(Optional.empty()), nextValueId);
        }
        storeToStack(instructions, stackBase, 0, thrown, tracking);
        Value mask = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.Const(mask, caughtMask(handlerByClassId)));
        Value classId = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntrinsicCall(Optional.of(classId), Intrinsic.THROW_DISPATCH,
                Optional.empty(), List.of(thrown, mask), List.of()));
        return new Thrown(dispatchTerminator(handlerByClassId, throwableClasses.size(), classId), nextValueId);
    }}
