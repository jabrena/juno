package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.ir.ArrayDeclaration;
import io.github.jabrena.juno.ir.ArrayElementType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 *
 * <p><b>Arrays</b> (there is no heap, so every array is a fixed-size C array) are supported in a deliberately
 * narrow, always-sound way, tracked by {@link ArrayTracking}:
 * <ul>
 *   <li>A JVM local slot is treated as a known-length array only when it is assigned via {@code astore}
 *       exactly once in the whole method (i.e. "effectively final"), immediately after {@code newarray} with
 *       a compile-time-constant count ({@link #computeSingleAssignmentArrayLocals}). Since that slot can then
 *       only ever hold that one array for its entire reachable lifetime, every load of it is safely
 *       known-length too, without needing a merge-aware, cross-block dataflow pass. Anything else holding an
 *       array reference (a parameter, a reassigned local) falls back to raw-pointer semantics: array
 *       load/store still compile, just unchecked, and {@code arraylength} is a compile error rather than a
 *       silently wrong answer.
 *   <li>Returning an array ({@code areturn}) is accepted only when the returned value directly traces to one
 *       of this method's own array-typed parameters — anything else (a locally {@code newarray}'d array, for
 *       instance) would return a pointer into this call's own stack frame, which dangles once it returns.
 * </ul>
 * Per-value knowledge ({@code arrayLength}, {@code parameterForwarded}) is tracked globally, since values are
 * never redefined. Propagating it back out of a stack-slot round trip needs one more piece of state — which
 * stack slot currently holds which known array — and that part is reset at the start of every block: unlike
 * JVM locals, stack slots are constantly reused as depth rises and falls, and two different branches could in
 * principle leave different arrays in the same slot before a merge.
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
        Descriptor methodDescriptor = Descriptor.parse(linked.method().descriptor());
        Map<Integer, Integer> entryDepths = computeEntryDepths(linked);
        Map<Integer, Integer> slotArrayLength = computeSingleAssignmentArrayLocals(linked);
        Set<Integer> arrayParameterSlots = arrayParameterSlots(methodDescriptor);
        ArrayTracking tracking = new ArrayTracking();
        List<ArrayDeclaration> arrayDeclarations = new ArrayList<>();
        List<IrBasicBlock> blocks = new ArrayList<>();
        int nextValueId = 0;
        for (BasicBlock block : linked.controlFlowGraph().blocks()) {
            List<IrInstruction> instructions = new ArrayList<>();
            int depth = entryDepths.getOrDefault(block.start(), 0);
            tracking.startBlock();
            IrTerminator terminator = null;
            for (Instruction instruction : block.instructions()) {
                int opcode = instruction.opcode();
                switch (opcode) {
                    case 0 -> { }
                    case 2 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, -1, tracking);
                        depth++;
                    }
                    case 3, 4, 5, 6, 7, 8 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, opcode - 3, tracking);
                        depth++;
                    }
                    case 9, 10 -> {
                        nextValueId = pushWideConst(instructions, stackBase, depth, nextValueId, opcode - 9, tracking);
                        depth += 2;
                    }
                    case 20 -> {
                        long value = linked.owner().constantPool().longValue(instruction.operandA());
                        nextValueId = pushWideConst(instructions, stackBase, depth, nextValueId, value, tracking);
                        depth += 2;
                    }
                    case 16, 17 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, instruction.operandA(), tracking);
                        depth++;
                    }
                    case 18, 19 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId,
                                linked.owner().constantPool().integer(instruction.operandA()), tracking);
                        depth++;
                    }
                    case 21, 25 -> {
                        nextValueId = pushLoad(instructions, stackBase, depth, nextValueId, instruction.operandA(),
                                slotArrayLength, arrayParameterSlots, tracking);
                        depth++;
                    }
                    case 22 -> {
                        nextValueId = pushWideLoad(instructions, stackBase, depth, nextValueId,
                                instruction.operandA(), tracking);
                        depth += 2;
                    }
                    case 30, 31, 32, 33 -> {
                        nextValueId = pushWideLoad(instructions, stackBase, depth, nextValueId, opcode - 30, tracking);
                        depth += 2;
                    }
                    case 26, 27, 28, 29 -> {
                        nextValueId = pushLoad(instructions, stackBase, depth, nextValueId, opcode - 26,
                                slotArrayLength, arrayParameterSlots, tracking);
                        depth++;
                    }
                    case 42, 43, 44, 45 -> {
                        nextValueId = pushLoad(instructions, stackBase, depth, nextValueId, opcode - 42,
                                slotArrayLength, arrayParameterSlots, tracking);
                        depth++;
                    }
                    case 54, 58 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.value()));
                    }
                    case 55 -> {
                        depth -= 2;
                        WidePopped popped = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.low()));
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA() + 1, popped.high()));
                    }
                    case 59, 60, 61, 62 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 59, popped.value()));
                    }
                    case 63, 64, 65, 66 -> {
                        depth -= 2;
                        WidePopped popped = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        int local = opcode - 63;
                        instructions.add(new IrInstruction.StoreLocal(local, popped.low()));
                        instructions.add(new IrInstruction.StoreLocal(local + 1, popped.high()));
                    }
                    case 75, 76, 77, 78 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 75, popped.value()));
                    }
                    case 87 -> depth--;
                    case 89 -> {
                        Value top = new Value(nextValueId++);
                        instructions.add(new IrInstruction.LoadLocal(top, stackBase + depth - 1));
                        tracking.recordPop(stackBase + depth - 1, top);
                        storeToStack(instructions, stackBase, depth, top, tracking);
                        depth++;
                    }
                    case 96 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.ADD, tracking);
                        depth--;
                    }
                    case 97 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.ADD, tracking);
                        depth -= 2;
                    }
                    case 100 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SUBTRACT, tracking);
                        depth--;
                    }
                    case 101 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SUBTRACT, tracking);
                        depth -= 2;
                    }
                    case 104 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.MULTIPLY, tracking);
                        depth--;
                    }
                    case 105 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.MULTIPLY, tracking);
                        depth -= 2;
                    }
                    case 108 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.DIVIDE, tracking);
                        depth--;
                    }
                    case 109 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.DIVIDE, tracking);
                        depth -= 2;
                    }
                    case 112 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.REMAINDER, tracking);
                        depth--;
                    }
                    case 113 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.REMAINDER, tracking);
                        depth -= 2;
                    }
                    case 116 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.NEGATE, tracking);
                    case 117 -> nextValueId = pushLongNegate(instructions, stackBase, depth, nextValueId, tracking);
                    case 120 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_LEFT, tracking);
                        depth--;
                    }
                    case 121 -> {
                        nextValueId = pushLongShift(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_LEFT, tracking);
                        depth--;
                    }
                    case 122 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_RIGHT, tracking);
                        depth--;
                    }
                    case 123 -> {
                        nextValueId = pushLongShift(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_RIGHT, tracking);
                        depth--;
                    }
                    case 124 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId,
                                BinaryOp.UNSIGNED_SHIFT_RIGHT, tracking);
                        depth--;
                    }
                    case 125 -> {
                        nextValueId = pushLongShift(instructions, stackBase, depth, nextValueId,
                                BinaryOp.UNSIGNED_SHIFT_RIGHT, tracking);
                        depth--;
                    }
                    case 126 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.AND, tracking);
                        depth--;
                    }
                    case 127 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.AND, tracking);
                        depth -= 2;
                    }
                    case 128 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.OR, tracking);
                        depth--;
                    }
                    case 129 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.OR, tracking);
                        depth -= 2;
                    }
                    case 130 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.XOR, tracking);
                        depth--;
                    }
                    case 131 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.XOR, tracking);
                        depth -= 2;
                    }
                    case 133 -> {
                        Popped value = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value targetLow = new Value(nextValueId++);
                        Value targetHigh = new Value(nextValueId++);
                        instructions.add(new IrInstruction.IntToLong(targetLow, targetHigh, value.value()));
                        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
                        depth += 2;
                    }
                    case 136 -> {
                        depth -= 2;
                        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = new Value(nextValueId++);
                        instructions.add(new IrInstruction.LongToInt(target, value.low(), value.high()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }
                    case 148 -> {
                        depth -= 2;
                        WidePopped right = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = right.nextValueId();
                        depth -= 2;
                        WidePopped left = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = left.nextValueId();
                        Value result = new Value(nextValueId++);
                        instructions.add(new IrInstruction.LongCompare(
                                result, left.low(), left.high(), right.low(), right.high()));
                        storeToStack(instructions, stackBase, depth, result, tracking);
                        depth++;
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
                    case 145 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_BYTE, tracking);
                    case 146 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_CHAR, tracking);
                    case 147 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_SHORT, tracking);
                    case 153, 154, 155, 156, 157, 158 -> {
                        Popped operand = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = operand.nextValueId();
                        Value zero = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Const(zero, 0));
                        Value condition = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Compare(condition, conditionOf(opcode, 153), operand.value(), zero));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                    }
                    case 159, 160, 161, 162, 163, 164 -> {
                        Popped right = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = right.nextValueId();
                        Popped left = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = left.nextValueId();
                        Value condition = new Value(nextValueId++);
                        instructions.add(new IrInstruction.Compare(
                                condition, conditionOf(opcode, 159), left.value(), right.value()));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                    }
                    case 167 -> terminator = new IrTerminator.Jump(((Terminator.Jump) block.terminator()).target());
                    case 172 -> {
                        Popped returned = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = returned.nextValueId();
                        terminator = new IrTerminator.Return(Optional.of(returned.value()));
                    }
                    case 176 -> {
                        Popped returned = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = returned.nextValueId();
                        if (!tracking.isParameterForward(returned.value())) {
                            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                                    + instruction.offset() + ": returning an array is only supported when directly "
                                    + "forwarding a received array parameter (e.g. `return arr;` where arr is a "
                                    + "parameter); a locally created or otherwise derived array would dangle once "
                                    + "this method returns");
                        }
                        terminator = new IrTerminator.Return(Optional.of(returned.value()));
                    }
                    case 177 -> terminator = new IrTerminator.Return(Optional.empty());
                    case 182, 184 -> {
                        Lowered lowered = lowerCall(linked, instruction, instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 46 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.INT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 51 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.BYTE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 52 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.CHAR);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 53 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.SHORT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 79 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.INT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 84 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.BYTE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 85 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.CHAR);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 86 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.SHORT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 188 -> {
                        ArrayElementType elementType = ArrayElementType.fromAtype(instruction.operandA())
                                .orElseThrow(() -> new CompileException(linked.method().reference().displayName()
                                        + " at bytecode offset " + instruction.offset()
                                        + ": newarray only supports boolean[]/byte[]/char[]/short[]/int[] (atype "
                                        + "4/8/5/9/10), got atype " + instruction.operandA()));
                        ConstPop length = popKnownConstant(instructions, stackBase, depth, linked, instruction, tracking);
                        depth = length.depth();
                        Value handle = new Value(nextValueId++);
                        instructions.add(new IrInstruction.NewArray(handle, elementType, length.value()));
                        arrayDeclarations.add(new ArrayDeclaration(handle, elementType, length.value()));
                        tracking.markKnownArray(handle, length.value());
                        storeToStack(instructions, stackBase, depth, handle, tracking);
                        depth++;
                    }
                    case 190 -> {
                        Popped array = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = array.nextValueId();
                        Integer knownLength = tracking.knownLength(array.value());
                        if (knownLength == null) {
                            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                                    + instruction.offset() + ": array length is not known at compile time here "
                                    + "(only supported on a local array created once in this method with a "
                                    + "compile-time-constant size)");
                        }
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, knownLength, tracking);
                        depth++;
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
                List.copyOf(arrayDeclarations), List.copyOf(blocks));
    }

    private Set<Integer> arrayParameterSlots(Descriptor methodDescriptor) {
        Set<Integer> slots = new HashSet<>();
        List<String> parameters = methodDescriptor.parameters();
        for (int index = 0; index < parameters.size(); index++) {
            if (Descriptor.isArrayType(parameters.get(index))) {
                slots.add(index);
            }
        }
        return slots;
    }

    /**
     * Finds every JVM local slot that is assigned via {@code astore} exactly once in the whole method,
     * immediately after {@code newarray} with a compile-time-constant count. Such a slot can only ever hold
     * that one array for its entire reachable lifetime (Java requires definite assignment before any read),
     * so every load of it is safely known-length without needing cross-block dataflow.
     */
    private Map<Integer, Integer> computeSingleAssignmentArrayLocals(LinkedMethod linked) {
        List<Instruction> all = linked.instructions();
        Map<Integer, Integer> storeCounts = new HashMap<>();
        Map<Integer, Integer> candidateLength = new HashMap<>();
        for (int index = 0; index < all.size(); index++) {
            Integer slot = astoreSlot(all.get(index));
            if (slot == null) {
                continue;
            }
            storeCounts.merge(slot, 1, Integer::sum);
            if (index < 2) {
                continue;
            }
            Instruction newArrayInstruction = all.get(index - 1);
            Instruction lengthPush = all.get(index - 2);
            if (newArrayInstruction.opcode() == 188
                    && ArrayElementType.fromAtype(newArrayInstruction.operandA()).isPresent()) {
                Integer length = constantPushValue(lengthPush, linked);
                if (length != null) {
                    candidateLength.put(slot, length);
                }
            }
        }
        Map<Integer, Integer> result = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : candidateLength.entrySet()) {
            if (storeCounts.get(entry.getKey()) == 1) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private Integer astoreSlot(Instruction instruction) {
        return switch (instruction.opcode()) {
            case 58 -> instruction.operandA();
            case 75, 76, 77, 78 -> instruction.opcode() - 75;
            default -> null;
        };
    }

    private Integer constantPushValue(Instruction instruction, LinkedMethod linked) {
        return switch (instruction.opcode()) {
            case 2 -> -1;
            case 3, 4, 5, 6, 7, 8 -> instruction.opcode() - 3;
            case 16, 17 -> instruction.operandA();
            case 18, 19 -> linked.owner().constantPool().integer(instruction.operandA());
            default -> null;
        };
    }

    /**
     * Pops the top of stack, requiring it to be a compile-time-constant literal pushed immediately before
     * (nothing else observed it in between) — un-emits that push and returns its value. Used for
     * {@code newarray}'s count, since there is no heap and every array must be a fixed-size C array.
     */
    private ConstPop popKnownConstant(List<IrInstruction> instructions, int stackBase, int depth,
                                       LinkedMethod linked, Instruction site, ArrayTracking tracking) {
        int newDepth = depth - 1;
        int slot = stackBase + newDepth;
        if (instructions.size() >= 2
                && instructions.get(instructions.size() - 1) instanceof IrInstruction.StoreLocal store
                && store.local() == slot
                && instructions.get(instructions.size() - 2) instanceof IrInstruction.Const constant
                && constant.target().equals(store.value())) {
            instructions.remove(instructions.size() - 1);
            instructions.remove(instructions.size() - 1);
            tracking.clearStackSlot(slot);
            return new ConstPop(constant.value(), newDepth);
        }
        throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                + site.offset() + ": array length must be a compile-time constant");
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
            case 0, 132, 145, 146, 147, 116, 117, 167, 177, 188, 190 -> 0;
            case 2, 3, 4, 5, 6, 7, 8, 16, 17, 18, 19, 21, 25, 26, 27, 28, 29, 42, 43, 44, 45, 89 -> 1;
            case 133 -> 1;
            case 9, 10, 20, 22, 30, 31, 32, 33 -> 2;
            case 54, 58, 59, 60, 61, 62, 75, 76, 77, 78, 87, 153, 154, 155, 156, 157, 158, 172, 176 -> -1;
            case 46, 51, 52, 53 -> -1;
            case 96, 100, 104, 108, 112, 120, 122, 124, 126, 128, 130 -> -1;
            case 121, 123, 125, 136 -> -1;
            case 55, 63, 64, 65, 66, 97, 101, 105, 109, 113, 127, 129, 131 -> -2;
            case 159, 160, 161, 162, 163, 164 -> -2;
            case 148 -> -3;
            case 79, 84, 85, 86 -> -3;
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
                               int stackBase, int depth, int nextValueId, ArrayTracking tracking) {
        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        Value[] arguments = new Value[descriptor.parameters().size()];
        for (int index = arguments.length - 1; index >= 0; index--) {
            Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
            nextValueId = popped.nextValueId();
            arguments[index] = popped.value();
        }
        Optional<Value> receiver = Optional.empty();
        if (instruction.opcode() == 182) {
            Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
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
            storeToStack(instructions, stackBase, depth, target, tracking);
            depth++;
        }
        return new Lowered(nextValueId, depth);
    }

    private Lowered lowerArrayLoad(List<IrInstruction> instructions, int stackBase, int depth,
                                    int nextValueId, ArrayTracking tracking, ArrayElementType elementType) {
        Popped index = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = index.nextValueId();
        Popped array = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = array.nextValueId();
        Integer knownLength = tracking.knownLength(array.value());
        if (knownLength != null) {
            instructions.add(new IrInstruction.BoundsCheck(index.value(), knownLength));
        }
        Value target = new Value(nextValueId++);
        instructions.add(new IrInstruction.ArrayLoad(target, elementType, array.value(), index.value()));
        storeToStack(instructions, stackBase, depth, target, tracking);
        depth++;
        return new Lowered(nextValueId, depth);
    }

    private Lowered lowerArrayStore(List<IrInstruction> instructions, int stackBase, int depth,
                                     int nextValueId, ArrayTracking tracking, ArrayElementType elementType) {
        Popped value = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = value.nextValueId();
        Popped index = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = index.nextValueId();
        Popped array = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = array.nextValueId();
        Integer knownLength = tracking.knownLength(array.value());
        if (knownLength != null) {
            instructions.add(new IrInstruction.BoundsCheck(index.value(), knownLength));
        }
        instructions.add(new IrInstruction.ArrayStore(elementType, array.value(), index.value(), value.value()));
        return new Lowered(nextValueId, depth);
    }

    private int pushConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int value,
                           ArrayTracking tracking) {
        Value target = new Value(nextValueId);
        instructions.add(new IrInstruction.Const(target, value));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        tracking.clearStackSlot(stackBase + depth);
        return nextValueId + 1;
    }

    private int pushLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int local,
                          Map<Integer, Integer> slotArrayLength, Set<Integer> arrayParameterSlots, ArrayTracking tracking) {
        Value target = new Value(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(target, local));
        Integer knownLength = slotArrayLength.get(local);
        if (knownLength != null) {
            tracking.markKnownArray(target, knownLength);
        }
        if (arrayParameterSlots.contains(local)) {
            tracking.markParameterForward(target);
        }
        storeToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    private int pushBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                            BinaryOp operation, ArrayTracking tracking) {
        int depth = depthBeforePush;
        Popped right = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = right.nextValueId();
        Popped left = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = left.nextValueId();
        Value target = new Value(nextValueId++);
        instructions.add(new IrInstruction.Binary(target, operation, left.value(), right.value()));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        tracking.clearStackSlot(stackBase + depth);
        return nextValueId;
    }

    private int pushUnary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                           UnaryOp operation, ArrayTracking tracking) {
        int depth = depthBeforePush - 1;
        Popped operand = pop(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = operand.nextValueId();
        Value target = new Value(nextValueId++);
        instructions.add(new IrInstruction.Unary(target, operation, operand.value()));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        tracking.clearStackSlot(stackBase + depth);
        return nextValueId;
    }

    /** Emits a StoreLocal to a stack slot and keeps {@code tracking} consistent with it. */
    private void storeToStack(List<IrInstruction> instructions, int stackBase, int depth, Value value, ArrayTracking tracking) {
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, value));
        tracking.recordPush(stackBase + depth, value);
    }

    private Popped pop(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId, ArrayTracking tracking) {
        Value value = new Value(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(value, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, value);
        return new Popped(value, nextValueId + 1);
    }

    /**
     * A {@code long} occupies two consecutive stack/local slots; by convention the lower-depth (lower-index)
     * slot holds the low 32 bits and the next one holds the high 32 bits, both for synthetic stack slots and
     * for JVM local slots (e.g. {@code lload n} reads locals {@code n} and {@code n + 1}).
     */
    private WidePopped popWide(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId, ArrayTracking tracking) {
        Value low = new Value(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(low, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, low);
        Value high = new Value(nextValueId + 1);
        instructions.add(new IrInstruction.LoadLocal(high, stackBase + depthAfterPop + 1));
        tracking.recordPop(stackBase + depthAfterPop + 1, high);
        return new WidePopped(low, high, nextValueId + 2);
    }

    /** Stores a long's two halves to a pair of stack slots; arrays are never wide, so both slots are defensively cleared. */
    private void storeWideToStack(List<IrInstruction> instructions, int stackBase, int depth, Value low, Value high, ArrayTracking tracking) {
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, low));
        tracking.clearStackSlot(stackBase + depth);
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth + 1, high));
        tracking.clearStackSlot(stackBase + depth + 1);
    }

    private int pushWideConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, long value,
                               ArrayTracking tracking) {
        Value low = new Value(nextValueId);
        Value high = new Value(nextValueId + 1);
        instructions.add(new IrInstruction.LongConst(low, high, value));
        storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        return nextValueId + 2;
    }

    private int pushWideLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int local,
                              ArrayTracking tracking) {
        Value low = new Value(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(low, local));
        Value high = new Value(nextValueId + 1);
        instructions.add(new IrInstruction.LoadLocal(high, local + 1));
        storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        return nextValueId + 2;
    }

    private int pushLongBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                                BinaryOp operation, ArrayTracking tracking) {
        int depth = depthBeforePush - 2;
        WidePopped right = popWide(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = right.nextValueId();
        depth -= 2;
        WidePopped left = popWide(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = left.nextValueId();
        Value targetLow = new Value(nextValueId++);
        Value targetHigh = new Value(nextValueId++);
        instructions.add(new IrInstruction.LongBinary(targetLow, targetHigh, operation,
                left.low(), left.high(), right.low(), right.high()));
        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
        return nextValueId;
    }

    /** {@code lshl}/{@code lshr}/{@code lushr}: the shift amount is a plain int, popped before the long value. */
    private int pushLongShift(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                               BinaryOp operation, ArrayTracking tracking) {
        int depth = depthBeforePush;
        Popped amount = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = amount.nextValueId();
        depth -= 2;
        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = value.nextValueId();
        Value targetLow = new Value(nextValueId++);
        Value targetHigh = new Value(nextValueId++);
        instructions.add(new IrInstruction.LongShift(targetLow, targetHigh, operation,
                value.low(), value.high(), amount.value()));
        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
        return nextValueId;
    }

    private int pushLongNegate(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                                ArrayTracking tracking) {
        int depth = depthBeforePush - 2;
        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = value.nextValueId();
        Value targetLow = new Value(nextValueId++);
        Value targetHigh = new Value(nextValueId++);
        instructions.add(new IrInstruction.LongNegate(targetLow, targetHigh, value.low(), value.high()));
        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
        return nextValueId;
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

    private record WidePopped(Value low, Value high, int nextValueId) {
    }

    private record Lowered(int nextValueId, int depth) {
    }

    private record ConstPop(int value, int depth) {
    }

    /**
     * Per-method array bookkeeping used to decide, at each array access, whether it is safe to bounds-check
     * (a known-length local array) and, at {@code areturn}, whether it is safe to return (a direct parameter
     * forward). {@code arrayLength}/{@code parameterForwarded} are keyed by {@link Value} and never reset —
     * values are single-assignment, so a fact about one is true for its whole lifetime. The stack-slot views
     * are reset at the start of every block (see the class-level docs for why).
     */
    private static final class ArrayTracking {
        private final Map<Value, Integer> arrayLength = new HashMap<>();
        private final Set<Value> parameterForwarded = new HashSet<>();
        private Map<Integer, Integer> currentStackSlotLength = new HashMap<>();
        private Set<Integer> currentStackSlotIsParameterForward = new HashSet<>();

        void startBlock() {
            currentStackSlotLength = new HashMap<>();
            currentStackSlotIsParameterForward = new HashSet<>();
        }

        void markKnownArray(Value value, int length) {
            arrayLength.put(value, length);
        }

        void markParameterForward(Value value) {
            parameterForwarded.add(value);
        }

        Integer knownLength(Value value) {
            return arrayLength.get(value);
        }

        boolean isParameterForward(Value value) {
            return parameterForwarded.contains(value);
        }

        void clearStackSlot(int slot) {
            currentStackSlotLength.remove(slot);
            currentStackSlotIsParameterForward.remove(slot);
        }

        void recordPush(int slot, Value value) {
            Integer length = arrayLength.get(value);
            if (length != null) {
                currentStackSlotLength.put(slot, length);
            } else {
                currentStackSlotLength.remove(slot);
            }
            if (parameterForwarded.contains(value)) {
                currentStackSlotIsParameterForward.add(slot);
            } else {
                currentStackSlotIsParameterForward.remove(slot);
            }
        }

        void recordPop(int slot, Value value) {
            Integer length = currentStackSlotLength.get(slot);
            if (length != null) {
                arrayLength.put(value, length);
            }
            if (currentStackSlotIsParameterForward.contains(slot)) {
                parameterForwarded.add(value);
            }
        }
    }
}
