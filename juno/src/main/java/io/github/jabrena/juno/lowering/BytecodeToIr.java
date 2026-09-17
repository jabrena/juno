package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lowers each reachable method's JVM bytecode into Juno IR: a non-SSA, block-structured form where every
 * operand-stack push/pop becomes an explicit {@link Value} produced or consumed by an {@link IrInstruction}.
 *
 * <p>The JVM operand stack is not single-assignment across blocks: two different predecessor blocks can each
 * push a value that a common successor consumes (e.g. a ternary expression), and whichever branch actually
 * ran at runtime is the one whose value must be read. So each stack position is represented as a synthetic
 * local slot (reusing {@link IrInstruction.StoreLocal}/{@link IrInstruction.LoadLocal}, sized using the
 * classfile's own {@code max_stack}) that every predecessor writes and every successor reads — exactly how
 * the pre-IR backend's single shared {@code stack[]} array behaved. Each block's true entry depth is computed
 * up front with a small forward pass over the {@link BasicBlock} partition from
 * {@link io.github.jabrena.juno.analysis.ControlFlowGraphBuilder}, since a block's depth cannot in general be
 * inferred just by reading blocks in textual order.
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
        int stackBase = linked.method().maxLocals();
        Map<Integer, Integer> entryDepths = computeEntryDepths(linked);
        List<IrBasicBlock> blocks = new ArrayList<>();
        int nextValueId = 0;
        for (BasicBlock block : linked.controlFlowGraph().blocks()) {
            List<IrInstruction> instructions = new ArrayList<>();
            int depth = entryDepths.getOrDefault(block.start(), 0);
            IrTerminator terminator = null;
            for (Instruction instruction : block.instructions()) {
                int opcode = instruction.opcode();
                switch (opcode) {
                    case 0 -> { }
                    case 2 -> nextValueId = pushConst(instructions, stackBase, depth++, nextValueId, -1);
                    case 3, 4, 5, 6, 7, 8 ->
                            nextValueId = pushConst(instructions, stackBase, depth++, nextValueId, opcode - 3);
                    case 16, 17 ->
                            nextValueId = pushConst(instructions, stackBase, depth++, nextValueId, instruction.operandA());
                    case 18, 19 -> nextValueId = pushConst(instructions, stackBase, depth++, nextValueId,
                            linked.owner().constantPool().integer(instruction.operandA()));
                    case 21, 25 ->
                            nextValueId = pushLoad(instructions, stackBase, depth++, nextValueId, instruction.operandA());
                    case 26, 27, 28, 29 ->
                            nextValueId = pushLoad(instructions, stackBase, depth++, nextValueId, opcode - 26);
                    case 42, 43, 44, 45 ->
                            nextValueId = pushLoad(instructions, stackBase, depth++, nextValueId, opcode - 42);
                    case 54, 58 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.value()));
                    }
                    case 59, 60, 61, 62 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 59, popped.value()));
                    }
                    case 75, 76, 77, 78 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 75, popped.value()));
                    }
                    case 87 -> depth--;
                    case 89 -> {
                        Value top = new Value(nextValueId++);
                        instructions.add(new IrInstruction.LoadLocal(top, stackBase + depth - 1));
                        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, top));
                        depth++;
                    }
                    case 96 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.ADD);
                        depth--;
                    }
                    case 100 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SUBTRACT);
                        depth--;
                    }
                    case 104 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.MULTIPLY);
                        depth--;
                    }
                    case 108 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.DIVIDE);
                        depth--;
                    }
                    case 112 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.REMAINDER);
                        depth--;
                    }
                    case 116 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.NEGATE);
                    case 120 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_LEFT);
                        depth--;
                    }
                    case 122 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_RIGHT);
                        depth--;
                    }
                    case 124 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId,
                                BinaryOp.UNSIGNED_SHIFT_RIGHT);
                        depth--;
                    }
                    case 126 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.AND);
                        depth--;
                    }
                    case 128 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.OR);
                        depth--;
                    }
                    case 130 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.XOR);
                        depth--;
                    }
                    case 132 -> {
                        Value loaded = new Value(nextValueId++);
                        instructions.add(new IrInstruction.LoadLocal(loaded, instruction.operandA()));
                        Value amount = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Const(amount, instruction.operandB()));
                        Value sum = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Binary(sum, BinaryOp.ADD, loaded, amount));
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), sum));
                    }
                    case 145 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_BYTE);
                    case 146 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_CHAR);
                    case 147 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_SHORT);
                    case 153, 154, 155, 156, 157, 158 -> {
                        Popped operand = pop(instructions, stackBase, --depth, nextValueId);
                        nextValueId = operand.nextValueId();
                        Value zero = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Const(zero, 0));
                        Value condition = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Compare(condition, conditionOf(opcode, 153), operand.value(), zero));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                    }
                    case 159, 160, 161, 162, 163, 164 -> {
                        Popped right = pop(instructions, stackBase, --depth, nextValueId);
                        nextValueId = right.nextValueId();
                        Popped left = pop(instructions, stackBase, --depth, nextValueId);
                        nextValueId = left.nextValueId();
                        Value condition = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Compare(
                                condition, conditionOf(opcode, 159), left.value(), right.value()));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                    }
                    case 167 -> terminator = new IrTerminator.Jump(((Terminator.Jump) block.terminator()).target());
                    case 172 -> {
                        Popped returned = pop(instructions, stackBase, --depth, nextValueId);
                        nextValueId = returned.nextValueId();
                        terminator = new IrTerminator.Return(Optional.of(returned.value()));
                    }
                    case 177 -> terminator = new IrTerminator.Return(Optional.empty());
                    case 182, 184 -> {
                        Lowered lowered = lowerCall(linked, instruction, instructions, stackBase, depth, nextValueId);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    default -> throw new CompileException(
                            "Juno IR lowering does not support opcode " + opcode);
                }
            }
            if (terminator == null) {
                terminator = new IrTerminator.Jump(((Terminator.Fallthrough) block.terminator()).target());
            }
            blocks.add(new IrBasicBlock(block.start(), List.copyOf(instructions), terminator));
        }
        return new IrMethod(linked.method().reference(), stackBase + linked.method().maxStack(), nextValueId,
                List.copyOf(blocks));
    }

    /**
     * Computes each reachable block's true operand-stack depth on entry, by walking the CFG from the method's
     * entry block (depth 0) and propagating each block's net push/pop effect to its successors. The JVM
     * verifier guarantees every predecessor of a block agrees on that block's entry depth, so a first-visit
     * BFS (no fixed-point iteration) is enough.
     */
    private Map<Integer, Integer> computeEntryDepths(LinkedMethod linked) {
        ControlFlowGraph cfg = linked.controlFlowGraph();
        Map<Integer, Integer> entryDepth = new HashMap<>();
        Deque<Integer> work = new ArrayDeque<>();
        int entryStart = cfg.entry().start();
        entryDepth.put(entryStart, 0);
        work.add(entryStart);
        while (!work.isEmpty()) {
            int blockStart = work.removeFirst();
            BasicBlock block = cfg.blockAt(blockStart).orElseThrow();
            int depth = entryDepth.get(blockStart);
            for (Instruction instruction : block.instructions()) {
                depth += stackDelta(linked, instruction);
            }
            for (int successor : successorsOf(block.terminator())) {
                Integer existing = entryDepth.get(successor);
                if (existing == null) {
                    entryDepth.put(successor, depth);
                    work.add(successor);
                } else if (existing != depth) {
                    throw new CompileException(linked.method().reference().displayName()
                            + ": inconsistent operand-stack depth entering block at offset " + successor
                            + " (" + existing + " from an earlier predecessor, " + depth
                            + " from block at offset " + blockStart + ")");
                }
            }
        }
        return entryDepth;
    }

    private List<Integer> successorsOf(Terminator terminator) {
        return switch (terminator) {
            case Terminator.Jump jump -> List.of(jump.target());
            case Terminator.Branch branch -> List.of(branch.trueTarget(), branch.falseTarget());
            case Terminator.Fallthrough fallthrough -> List.of(fallthrough.target());
            case Terminator.Return ignored -> List.of();
        };
    }

    private int stackDelta(LinkedMethod linked, Instruction instruction) {
        int opcode = instruction.opcode();
        return switch (opcode) {
            case 0, 132, 145, 146, 147, 116, 167, 177 -> 0;
            case 2, 3, 4, 5, 6, 7, 8, 16, 17, 18, 19, 21, 25, 26, 27, 28, 29, 42, 43, 44, 45, 89 -> 1;
            case 54, 58, 59, 60, 61, 62, 75, 76, 77, 78, 87, 153, 154, 155, 156, 157, 158, 172 -> -1;
            case 96, 100, 104, 108, 112, 120, 122, 124, 126, 128, 130 -> -1;
            case 159, 160, 161, 162, 163, 164 -> -2;
            case 182, 184 -> {
                MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
                Descriptor descriptor = Descriptor.parse(called.descriptor());
                int consumed = descriptor.parameters().size() + (opcode == 182 ? 1 : 0);
                int produced = descriptor.returnsVoid() ? 0 : 1;
                yield produced - consumed;
            }
            default -> throw new CompileException("Juno IR lowering does not support opcode " + opcode);
        };
    }

    private Lowered lowerCall(LinkedMethod linked, Instruction instruction, List<IrInstruction> instructions,
                               int stackBase, int depth, int nextValueId) {
        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        Value[] arguments = new Value[descriptor.parameters().size()];
        for (int index = arguments.length - 1; index >= 0; index--) {
            Popped popped = pop(instructions, stackBase, --depth, nextValueId);
            nextValueId = popped.nextValueId();
            arguments[index] = popped.value();
        }
        Optional<Value> receiver = Optional.empty();
        if (instruction.opcode() == 182) {
            Popped popped = pop(instructions, stackBase, --depth, nextValueId);
            nextValueId = popped.nextValueId();
            receiver = Optional.of(popped.value());
        }

        Value target = null;
        if (!descriptor.returnsVoid()) {
            target = new Value(nextValueId++);
        }

        Optional<Intrinsic> intrinsic = IntrinsicRegistry.resolve(called);
        if (intrinsic.isPresent()) {
            instructions.add(new IrInstruction.IntrinsicCall(
                    Optional.ofNullable(target), intrinsic.get(), receiver, List.of(arguments)));
        } else {
            instructions.add(new IrInstruction.Call(Optional.ofNullable(target), called, List.of(arguments)));
        }
        if (target != null) {
            instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
            depth++;
        }
        return new Lowered(nextValueId, depth);
    }

    private int pushConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int value) {
        Value target = new Value(nextValueId);
        instructions.add(new IrInstruction.Const(target, value));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        return nextValueId + 1;
    }

    private int pushLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int local) {
        Value target = new Value(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(target, local));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        return nextValueId + 1;
    }

    private int pushBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                            BinaryOp operation) {
        int depth = depthBeforePush;
        Popped right = pop(instructions, stackBase, --depth, nextValueId);
        nextValueId = right.nextValueId();
        Popped left = pop(instructions, stackBase, --depth, nextValueId);
        nextValueId = left.nextValueId();
        Value target = new Value(nextValueId++);
        instructions.add(new IrInstruction.Binary(target, operation, left.value(), right.value()));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        return nextValueId;
    }

    private int pushUnary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                           UnaryOp operation) {
        int depth = depthBeforePush - 1;
        Popped operand = pop(instructions, stackBase, depth, nextValueId);
        nextValueId = operand.nextValueId();
        Value target = new Value(nextValueId++);
        instructions.add(new IrInstruction.Unary(target, operation, operand.value()));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        return nextValueId;
    }

    private Popped pop(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId) {
        Value value = new Value(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(value, stackBase + depthAfterPop));
        return new Popped(value, nextValueId + 1);
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

    private record Popped(Value value, int nextValueId) {
    }

    private record Lowered(int nextValueId, int depth) {
    }
}
