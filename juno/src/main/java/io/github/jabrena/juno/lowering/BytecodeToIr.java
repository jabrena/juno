package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.Program;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Lowers each reachable method's JVM bytecode into Juno IR: a non-SSA, block-structured form where every
 * operand-stack push/pop becomes an explicit {@link Value} produced or consumed by an {@link IrInstruction}.
 * The operand stack is simulated once, in bytecode offset order, across the whole method — matching how the
 * generated C++'s {@code stack[]} array behaves at runtime — and split into blocks using the already-built
 * {@link BasicBlock} partition from {@link io.github.jabrena.juno.analysis.ControlFlowGraphBuilder}.
 */
public final class BytecodeToIr {
    public IrProgram lower(Program program) {
        List<IrMethod> methods = new ArrayList<>();
        for (LinkedMethod linked : program.methods()) {
            methods.add(lower(linked));
        }
        return new IrProgram(program.entryPoint(), List.copyOf(methods));
    }

    public IrMethod lower(LinkedMethod linked) {
        Deque<Value> stack = new ArrayDeque<>();
        List<IrBasicBlock> blocks = new ArrayList<>();
        int nextValueId = 0;
        for (BasicBlock block : linked.controlFlowGraph().blocks()) {
            List<IrInstruction> instructions = new ArrayList<>();
            IrTerminator terminator = null;
            for (Instruction instruction : block.instructions()) {
                int opcode = instruction.opcode();
                switch (opcode) {
                    case 0 -> { }
                    case 2 -> nextValueId = pushConst(instructions, stack, nextValueId, -1);
                    case 3, 4, 5, 6, 7, 8 -> nextValueId = pushConst(instructions, stack, nextValueId, opcode - 3);
                    case 16, 17 -> nextValueId = pushConst(instructions, stack, nextValueId, instruction.operandA());
                    case 18, 19 -> nextValueId = pushConst(instructions, stack, nextValueId,
                            linked.owner().constantPool().integer(instruction.operandA()));
                    case 21, 25 -> nextValueId = pushLoad(instructions, stack, nextValueId, instruction.operandA());
                    case 26, 27, 28, 29 -> nextValueId = pushLoad(instructions, stack, nextValueId, opcode - 26);
                    case 42, 43, 44, 45 -> nextValueId = pushLoad(instructions, stack, nextValueId, opcode - 42);
                    case 54, 58 -> instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), stack.pop()));
                    case 59, 60, 61, 62 -> instructions.add(new IrInstruction.StoreLocal(opcode - 59, stack.pop()));
                    case 75, 76, 77, 78 -> instructions.add(new IrInstruction.StoreLocal(opcode - 75, stack.pop()));
                    case 87 -> stack.pop();
                    case 89 -> stack.push(stack.peek());
                    case 96 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.ADD);
                    case 100 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.SUBTRACT);
                    case 104 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.MULTIPLY);
                    case 108 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.DIVIDE);
                    case 112 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.REMAINDER);
                    case 116 -> nextValueId = pushUnary(instructions, stack, nextValueId, UnaryOp.NEGATE);
                    case 120 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.SHIFT_LEFT);
                    case 122 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.SHIFT_RIGHT);
                    case 124 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.UNSIGNED_SHIFT_RIGHT);
                    case 126 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.AND);
                    case 128 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.OR);
                    case 130 -> nextValueId = pushBinary(instructions, stack, nextValueId, BinaryOp.XOR);
                    case 132 -> {
                        Value loaded = new Value(nextValueId++);
                        instructions.add(new IrInstruction.LoadLocal(loaded, instruction.operandA()));
                        Value amount = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Const(amount, instruction.operandB()));
                        Value sum = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Binary(sum, BinaryOp.ADD, loaded, amount));
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), sum));
                    }
                    case 145 -> nextValueId = pushUnary(instructions, stack, nextValueId, UnaryOp.TO_BYTE);
                    case 146 -> nextValueId = pushUnary(instructions, stack, nextValueId, UnaryOp.TO_CHAR);
                    case 147 -> nextValueId = pushUnary(instructions, stack, nextValueId, UnaryOp.TO_SHORT);
                    case 153, 154, 155, 156, 157, 158 -> {
                        Value operand = stack.pop();
                        Value zero = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Const(zero, 0));
                        Value condition = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Compare(condition, conditionOf(opcode, 153), operand, zero));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                    }
                    case 159, 160, 161, 162, 163, 164 -> {
                        Value right = stack.pop();
                        Value left = stack.pop();
                        Value condition = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Compare(condition, conditionOf(opcode, 159), left, right));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                    }
                    case 167 -> terminator = new IrTerminator.Jump(((Terminator.Jump) block.terminator()).target());
                    case 172 -> terminator = new IrTerminator.Return(Optional.of(stack.pop()));
                    case 177 -> terminator = new IrTerminator.Return(Optional.empty());
                    case 182, 184 -> nextValueId = lowerCall(linked, instruction, instructions, stack, nextValueId);
                    default -> throw new CompileException(
                            "Juno IR lowering does not support opcode " + opcode);
                }
            }
            if (terminator == null) {
                terminator = new IrTerminator.Jump(((Terminator.Fallthrough) block.terminator()).target());
            }
            blocks.add(new IrBasicBlock(block.start(), List.copyOf(instructions), terminator));
        }
        return new IrMethod(linked.method().reference(), linked.method().maxLocals(), nextValueId, List.copyOf(blocks));
    }

    private int lowerCall(LinkedMethod linked, Instruction instruction, List<IrInstruction> instructions,
                           Deque<Value> stack, int nextValueId) {
        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        Value[] arguments = new Value[descriptor.parameters().size()];
        for (int index = arguments.length - 1; index >= 0; index--) {
            arguments[index] = stack.pop();
        }
        Optional<Value> receiver = instruction.opcode() == 182 ? Optional.of(stack.pop()) : Optional.empty();

        int updatedNextValueId = nextValueId;
        Value target = null;
        if (!descriptor.returnsVoid()) {
            target = new Value(updatedNextValueId++);
        }

        Optional<Intrinsic> intrinsic = IntrinsicRegistry.resolve(called);
        if (intrinsic.isPresent()) {
            instructions.add(new IrInstruction.IntrinsicCall(
                    Optional.ofNullable(target), intrinsic.get(), receiver, List.of(arguments)));
        } else {
            instructions.add(new IrInstruction.Call(Optional.ofNullable(target), called, List.of(arguments)));
        }
        if (target != null) {
            stack.push(target);
        }
        return updatedNextValueId;
    }

    private int pushConst(List<IrInstruction> instructions, Deque<Value> stack, int nextValueId, int value) {
        Value target = new Value(nextValueId);
        instructions.add(new IrInstruction.Const(target, value));
        stack.push(target);
        return nextValueId + 1;
    }

    private int pushLoad(List<IrInstruction> instructions, Deque<Value> stack, int nextValueId, int local) {
        Value target = new Value(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(target, local));
        stack.push(target);
        return nextValueId + 1;
    }

    private int pushBinary(List<IrInstruction> instructions, Deque<Value> stack, int nextValueId, BinaryOp operation) {
        Value right = stack.pop();
        Value left = stack.pop();
        Value target = new Value(nextValueId);
        instructions.add(new IrInstruction.Binary(target, operation, left, right));
        stack.push(target);
        return nextValueId + 1;
    }

    private int pushUnary(List<IrInstruction> instructions, Deque<Value> stack, int nextValueId, UnaryOp operation) {
        Value operand = stack.pop();
        Value target = new Value(nextValueId);
        instructions.add(new IrInstruction.Unary(target, operation, operand));
        stack.push(target);
        return nextValueId + 1;
    }

    private Condition conditionOf(int opcode, int base) {
        return switch (opcode - base) {
            case 0 -> Condition.EQUAL;
            case 1 -> Condition.NOT_EQUAL;
            case 2 -> Condition.LESS_THAN;
            case 3 -> Condition.GREATER_EQUAL;
            case 4 -> Condition.GREATER_THAN;
            case 5 -> Condition.LESS_EQUAL;
            default -> throw new IllegalStateException("Unexpected comparison opcode " + opcode);
        };
    }
}
