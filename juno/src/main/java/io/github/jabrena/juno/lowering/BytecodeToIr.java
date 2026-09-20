package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.linker.RecordSupport;
import io.github.jabrena.juno.ir.ArrayDeclaration;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.FloatBinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.JunoType;
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
import java.util.stream.Collectors;

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
 * narrow, always-sound way, tracked by {@link ValueTracking}:
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
    private static final MethodRef DRAW_TEXT_METHOD = new MethodRef("io/github/jabrena/juno/api/led/LedCanvas",
            "drawText", "([[ZLjava/lang/String;II)V");
    private static final MethodRef DRAW_CHAR_METHOD = new MethodRef("io/github/jabrena/juno/api/led/LedCanvas",
            "drawChar", "([[ZIII)V");
    /** {@code io.github.jabrena.juno.api.led.LedMatrixFontAscii.GLYPH_WIDTH + 1} (a 5-wide glyph plus a
     * 1-column gap), kept as a literal so codegen doesn't depend on a specific font's internals; keep
     * the two in sync if the font's geometry ever changes. */
    private static final int DRAW_TEXT_CHAR_SPACING = 6;

    private final BytecodeDecoder decoder = new BytecodeDecoder();
    private final Map<String, List<FieldInfo>> validatedRecords = new HashMap<>();

    public IrProgram lower(Program program) {
        List<IrMethod> methods = new ArrayList<>();
        for (LinkedMethod linked : program.methods()) {
            methods.add(lower(linked, program.classes()));
        }
        return new IrProgram(program.entryPoint(), List.copyOf(methods));
    }

    /** Convenience overload for callers with no enum classes to resolve (e.g. hand-built {@link LinkedMethod}s in tests). */
    public IrMethod lower(LinkedMethod linked) {
        return lower(linked, Map.of());
    }

    public IrMethod lower(LinkedMethod linked, Map<String, JavaClass> classes) {
        int stackBase = linked.method().maxLocals();
        Descriptor methodDescriptor = Descriptor.parse(linked.method().descriptor());
        Map<Integer, Integer> entryDepths = computeEntryDepths(linked);
        Map<Integer, Integer> slotArrayLength = computeSingleAssignmentArrayLocals(linked);
        Set<Integer> arrayParameterSlots = arrayParameterSlots(methodDescriptor, linked.method().isStatic());
        Set<Integer> singleAssignmentLocals = computeSingleAssignmentLocals(linked);
        Map<Integer, RecordInstance> slotRecordInstance = new HashMap<>();
        Map<Integer, String> slotStringInstance = new HashMap<>();
        ValueTracking tracking = new ValueTracking();
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
                    case 1 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, 0, tracking);
                        depth++;
                    }
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
                    case 11, 12, 13 -> {
                        nextValueId = pushFloatConst(instructions, stackBase, depth, nextValueId,
                                (float) (opcode - 11), tracking);
                        depth++;
                    }
                    case 14, 15 -> {
                        nextValueId = pushDoubleConst(instructions, stackBase, depth, nextValueId,
                                (double) (opcode - 14), tracking);
                        depth += 2;
                    }
                    case 20 -> {
                        if (linked.owner().constantPool().isDouble(instruction.operandA())) {
                            nextValueId = pushDoubleConst(instructions, stackBase, depth, nextValueId,
                                    linked.owner().constantPool().doubleValue(instruction.operandA()), tracking);
                        } else {
                            long value = linked.owner().constantPool().longValue(instruction.operandA());
                            nextValueId = pushWideConst(instructions, stackBase, depth, nextValueId, value, tracking);
                        }
                        depth += 2;
                    }
                    case 16, 17 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, instruction.operandA(), tracking);
                        depth++;
                    }
                    case 18, 19 -> {
                        if (linked.owner().constantPool().isFloat(instruction.operandA())) {
                            nextValueId = pushFloatConst(instructions, stackBase, depth, nextValueId,
                                    linked.owner().constantPool().floatValue(instruction.operandA()), tracking);
                        } else if (linked.owner().constantPool().isString(instruction.operandA())) {
                            nextValueId = pushStringConst(instructions, stackBase, depth, nextValueId,
                                    linked.owner().constantPool().string(instruction.operandA()), tracking);
                        } else {
                            nextValueId = pushConst(instructions, stackBase, depth, nextValueId,
                                    linked.owner().constantPool().integer(instruction.operandA()), tracking);
                        }
                        depth++;
                    }
                    case 21, 25 -> {
                        nextValueId = pushLoad(instructions, stackBase, depth, nextValueId, instruction.operandA(),
                                slotArrayLength, arrayParameterSlots, slotRecordInstance, slotStringInstance, tracking);
                        depth++;
                    }
                    case 22 -> {
                        nextValueId = pushWideLoad(instructions, stackBase, depth, nextValueId,
                                instruction.operandA(), tracking);
                        depth += 2;
                    }
                    case 23 -> {
                        nextValueId = pushFloatLoad(instructions, stackBase, depth, nextValueId,
                                instruction.operandA(), tracking);
                        depth++;
                    }
                    case 24 -> {
                        nextValueId = pushDoubleLoad(instructions, stackBase, depth, nextValueId,
                                instruction.operandA(), tracking);
                        depth += 2;
                    }
                    case 30, 31, 32, 33 -> {
                        nextValueId = pushWideLoad(instructions, stackBase, depth, nextValueId, opcode - 30, tracking);
                        depth += 2;
                    }
                    case 34, 35, 36, 37 -> {
                        nextValueId = pushFloatLoad(instructions, stackBase, depth, nextValueId,
                                opcode - 34, tracking);
                        depth++;
                    }
                    case 38, 39, 40, 41 -> {
                        nextValueId = pushDoubleLoad(instructions, stackBase, depth, nextValueId,
                                opcode - 38, tracking);
                        depth += 2;
                    }
                    case 26, 27, 28, 29 -> {
                        nextValueId = pushLoad(instructions, stackBase, depth, nextValueId, opcode - 26,
                                slotArrayLength, arrayParameterSlots, slotRecordInstance, slotStringInstance, tracking);
                        depth++;
                    }
                    case 42, 43, 44, 45 -> {
                        nextValueId = pushLoad(instructions, stackBase, depth, nextValueId, opcode - 42,
                                slotArrayLength, arrayParameterSlots, slotRecordInstance, slotStringInstance, tracking);
                        depth++;
                    }
                    case 54, 58 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.value()));
                        trackRecordLocalIfSingleAssignment(instruction.operandA(), popped.value(), tracking,
                                singleAssignmentLocals, slotRecordInstance);
                        trackStringLocalIfSingleAssignment(instruction.operandA(), popped.value(), tracking,
                                singleAssignmentLocals, slotStringInstance);
                    }
                    case 55 -> {
                        depth -= 2;
                        WidePopped popped = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.low()));
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA() + 1, popped.high()));
                    }
                    case 56 -> {
                        Popped popped = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.value()));
                    }
                    case 57 -> {
                        depth -= 2;
                        Popped popped = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.value()));
                    }
                    case 59, 60, 61, 62 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 59, popped.value()));
                        trackRecordLocalIfSingleAssignment(opcode - 59, popped.value(), tracking,
                                singleAssignmentLocals, slotRecordInstance);
                        trackStringLocalIfSingleAssignment(opcode - 59, popped.value(), tracking,
                                singleAssignmentLocals, slotStringInstance);
                    }
                    case 63, 64, 65, 66 -> {
                        depth -= 2;
                        WidePopped popped = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        int local = opcode - 63;
                        instructions.add(new IrInstruction.StoreLocal(local, popped.low()));
                        instructions.add(new IrInstruction.StoreLocal(local + 1, popped.high()));
                    }
                    case 67, 68, 69, 70 -> {
                        Popped popped = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 67, popped.value()));
                    }
                    case 71, 72, 73, 74 -> {
                        depth -= 2;
                        Popped popped = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 71, popped.value()));
                    }
                    case 75, 76, 77, 78 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 75, popped.value()));
                        trackRecordLocalIfSingleAssignment(opcode - 75, popped.value(), tracking,
                                singleAssignmentLocals, slotRecordInstance);
                        trackStringLocalIfSingleAssignment(opcode - 75, popped.value(), tracking,
                                singleAssignmentLocals, slotStringInstance);
                    }
                    case 87 -> depth--;
                    case 88 -> depth -= 2;
                    case 89 -> {
                        JunoType type = tracking.stackSlotType(stackBase + depth - 1);
                        Value top = new Value(nextValueId++, type == null ? JunoType.INT32 : type);
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
                    case 98 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.ADD, tracking);
                        depth--;
                    }
                    case 99 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.ADD, tracking);
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
                    case 102 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.SUBTRACT, tracking);
                        depth--;
                    }
                    case 103 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.SUBTRACT, tracking);
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
                    case 106 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.MULTIPLY, tracking);
                        depth--;
                    }
                    case 107 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.MULTIPLY, tracking);
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
                    case 110 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.DIVIDE, tracking);
                        depth--;
                    }
                    case 111 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.DIVIDE, tracking);
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
                    case 114 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.REMAINDER, tracking);
                        depth--;
                    }
                    case 115 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.REMAINDER, tracking);
                        depth -= 2;
                    }
                    case 116 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.NEGATE, tracking);
                    case 117 -> nextValueId = pushLongNegate(instructions, stackBase, depth, nextValueId, tracking);
                    case 118 -> nextValueId = pushFloatNegate(instructions, stackBase, depth, nextValueId, tracking);
                    case 119 -> nextValueId = pushDoubleNegate(instructions, stackBase, depth, nextValueId, tracking);
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
                        Value targetLow = Value.int32(nextValueId++);
                        Value targetHigh = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.IntToLong(targetLow, targetHigh, value.value()));
                        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
                        depth += 2;
                    }
                    case 134 -> {
                        Popped value = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float32(nextValueId++);
                        instructions.add(new IrInstruction.IntToFloat(target, value.value()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }
                    case 135 -> {
                        Popped value = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float64(nextValueId++);
                        instructions.add(new IrInstruction.IntToDouble(target, value.value()));
                        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
                        depth += 2;
                    }
                    case 136 -> {
                        depth -= 2;
                        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.LongToInt(target, value.low(), value.high()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }
                    case 137 -> {
                        depth -= 2;
                        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float32(nextValueId++);
                        instructions.add(new IrInstruction.LongToFloat(target, value.low(), value.high()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }
                    case 138 -> {
                        depth -= 2;
                        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float64(nextValueId++);
                        instructions.add(new IrInstruction.LongToDouble(target, value.low(), value.high()));
                        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
                        depth += 2;
                    }
                    case 139 -> {
                        Popped value = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.FloatToInt(target, value.value()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }
                    case 140 -> {
                        Popped value = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value targetLow = Value.int32(nextValueId++);
                        Value targetHigh = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.FloatToLong(targetLow, targetHigh, value.value()));
                        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
                        depth += 2;
                    }
                    case 141 -> {
                        Popped value = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float64(nextValueId++);
                        instructions.add(new IrInstruction.FloatToDouble(target, value.value()));
                        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
                        depth += 2;
                    }
                    case 142 -> {
                        depth -= 2;
                        Popped value = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.DoubleToInt(target, value.value()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }
                    case 143 -> {
                        depth -= 2;
                        Popped value = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value targetLow = Value.int32(nextValueId++);
                        Value targetHigh = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.DoubleToLong(targetLow, targetHigh, value.value()));
                        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
                        depth += 2;
                    }
                    case 144 -> {
                        depth -= 2;
                        Popped value = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float32(nextValueId++);
                        instructions.add(new IrInstruction.DoubleToFloat(target, value.value()));
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
                        Value result = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.LongCompare(
                                result, left.low(), left.high(), right.low(), right.high()));
                        storeToStack(instructions, stackBase, depth, result, tracking);
                        depth++;
                    }
                    case 149, 150 -> {
                        Popped right = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = right.nextValueId();
                        Popped left = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = left.nextValueId();
                        Value result = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.FloatCompare(
                                result, left.value(), right.value(), opcode == 149 ? -1 : 1));
                        storeToStack(instructions, stackBase, depth, result, tracking);
                        depth++;
                    }
                    case 151, 152 -> {
                        depth -= 2;
                        Popped right = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = right.nextValueId();
                        depth -= 2;
                        Popped left = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = left.nextValueId();
                        Value result = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.DoubleCompare(
                                result, left.value(), right.value(), opcode == 151 ? -1 : 1));
                        storeToStack(instructions, stackBase, depth, result, tracking);
                        depth++;
                    }
                    case 132 -> {
                        Value loaded = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.LoadLocal(loaded, instruction.operandA()));
                        Value amount = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Const(amount, instruction.operandB()));
                        Value sum = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Binary(sum, BinaryOp.ADD, loaded, amount));
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), sum));
                    }
                    case 178 -> {
                        FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
                        Integer ordinal = resolveEnumOrdinal(field, classes);
                        if (ordinal != null) {
                            nextValueId = pushConst(instructions, stackBase, depth, nextValueId, ordinal, tracking);
                            depth++;
                        } else if (field.name().startsWith("$SwitchMap$")) {
                            List<Integer> mapping = resolveEnumSwitchMap(field, classes);
                            Value target = Value.int32(nextValueId++);
                            instructions.add(new IrInstruction.IntArrayConst(target, mapping));
                            tracking.markKnownArray(target, mapping.size());
                            storeToStack(instructions, stackBase, depth, target, tracking);
                            depth++;
                        } else {
                            JunoType type = validateStaticField(linked, instruction, field, classes);
                            Value target = new Value(nextValueId++, type);
                            instructions.add(new IrInstruction.LoadStatic(target, field));
                            if (type == JunoType.INT64) {
                                Value low = Value.int32(nextValueId++);
                                Value high = Value.int32(nextValueId++);
                                instructions.add(new IrInstruction.UnpackLong(low, high, target));
                                storeWideToStack(instructions, stackBase, depth, low, high, tracking);
                            } else if (type == JunoType.FLOAT64) {
                                storeDoubleToStack(instructions, stackBase, depth, target, tracking);
                            } else {
                                storeToStack(instructions, stackBase, depth, target, tracking);
                            }
                            depth += type.jvmSlots();
                        }
                    }
                    case 179 -> {
                        FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
                        JunoType type = validateStaticField(linked, instruction, field, classes);
                        depth -= type.jvmSlots();
                        if (type == JunoType.INT64) {
                            WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
                            nextValueId = value.nextValueId();
                            Value packed = Value.int64(nextValueId++);
                            instructions.add(new IrInstruction.PackLong(packed, value.low(), value.high()));
                            instructions.add(new IrInstruction.StoreStatic(field, packed));
                        } else {
                            Popped value = switch (type) {
                                case INT32 -> pop(instructions, stackBase, depth, nextValueId, tracking);
                                case FLOAT32 -> popFloat(instructions, stackBase, depth, nextValueId, tracking);
                                case FLOAT64 -> popDouble(instructions, stackBase, depth, nextValueId, tracking);
                                case INT64 -> throw new IllegalStateException("handled above");
                            };
                            nextValueId = value.nextValueId();
                            instructions.add(new IrInstruction.StoreStatic(field, value.value()));
                        }
                    }
                    case 180 -> {
                        Lowered lowered = lowerFieldLoad(linked, instruction, instructions, stackBase, depth,
                                nextValueId, tracking, classes);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 181 -> {
                        Lowered lowered = lowerFieldStore(linked, instruction, instructions, stackBase, depth,
                                nextValueId, tracking, classes);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 145 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_BYTE, tracking);
                    case 146 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_CHAR, tracking);
                    case 147 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_SHORT, tracking);
                    case 153, 154, 155, 156, 157, 158 -> {
                        Popped operand = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = operand.nextValueId();
                        Value zero = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Const(zero, 0));
                        Value condition = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Compare(condition, conditionOf(opcode, 153), operand.value(), zero));
                        Terminator.Branch branch = (Terminator.Branch) block.terminator();
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
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
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
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
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
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
                        terminator = new IrTerminator.Branch(condition, branch.trueTarget(), branch.falseTarget());
                    }
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
                        instructions.add(new IrInstruction.Panic());
                        terminator = new IrTerminator.Jump(block.start());
                    }
                    case 182 -> {
                        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
                        Lowered lowered = isEnumOrdinal(classes, called)
                                ? lowerEnumOrdinal(instructions, stackBase, depth, nextValueId, tracking)
                                : lowerCall(linked, instruction, instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 184 -> {
                        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
                        Lowered lowered = isEnumValues(classes, called)
                                ? lowerEnumValues(called, instructions, stackBase, depth, nextValueId, tracking, classes)
                                : isCompileTimeGetenv(called)
                                        ? lowerCompileTimeGetenv(linked, instruction, instructions, stackBase, depth,
                                                nextValueId, tracking)
                                        : isDrawTextCall(called)
                                                ? lowerDrawText(linked, instruction, instructions, stackBase, depth,
                                                        nextValueId, tracking)
                                                : lowerCall(linked, instruction, instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 183 -> {
                        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
                        Lowered lowered = isStringBuilderConstruction(called)
                                ? lowerStringBuilderConstruction(instructions, stackBase, depth, nextValueId, tracking)
                                : isRuntimeBaseConstructor(called)
                                        ? discardInstanceCall(called, instructions, stackBase, depth, nextValueId, tracking)
                                        : lowerCall(linked, instruction, instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 187 -> {
                        String className = linked.owner().constantPool().className(instruction.operandA());
                        JavaClass allocatedClass = classes.get(className);
                        if (className.startsWith("java/lang/")) {
                            nextValueId = pushConst(instructions, stackBase, depth, nextValueId, 0, tracking);
                            depth++;
                            break;
                        }
                        if (allocatedClass == null) {
                            throw new CompileException(linked.method().reference().displayName()
                                    + " at bytecode offset " + instruction.offset()
                                    + ": object class is not available for closed-world allocation: " + className);
                        }
                        if (!allocatedClass.isFinal()) {
                            throw new CompileException(linked.method().reference().displayName()
                                    + " at bytecode offset " + instruction.offset()
                                    + ": allocated classes must be final for statically resolved dispatch: "
                                    + className.replace('/', '.'));
                        }
                        Value object = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.NewObject(object, className));
                        storeToStack(instructions, stackBase, depth, object, tracking);
                        depth++;
                    }
                    case 46 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.INT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 47 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.LONG);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 48 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.FLOAT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 49 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.DOUBLE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 50 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.REFERENCE);
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
                    case 80 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.LONG);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 81 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.FLOAT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 82 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.DOUBLE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
                    case 83 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.REFERENCE);
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
                                        + ": unsupported primitive array atype " + instruction.operandA()));
                        ConstPop length = popKnownConstant(instructions, stackBase, depth, linked, instruction, tracking);
                        depth = length.depth();
                        Value handle = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.NewArray(handle, elementType, length.value()));
                        arrayDeclarations.add(new ArrayDeclaration(handle, elementType, length.value()));
                        tracking.markKnownArray(handle, length.value());
                        storeToStack(instructions, stackBase, depth, handle, tracking);
                        depth++;
                    }
                    case 189 -> {
                        ConstPop length = popKnownConstant(instructions, stackBase, depth, linked, instruction, tracking);
                        depth = length.depth();
                        Value handle = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.NewArray(handle, ArrayElementType.REFERENCE, length.value()));
                        arrayDeclarations.add(new ArrayDeclaration(handle, ArrayElementType.REFERENCE, length.value()));
                        tracking.markKnownArray(handle, length.value());
                        storeToStack(instructions, stackBase, depth, handle, tracking);
                        depth++;
                    }
                    case 197 -> {
                        int dimensions = instruction.operandB();
                        List<Integer> sizes = new ArrayList<>();
                        for (int index = dimensions - 1; index >= 0; index--) {
                            ConstPop size = popKnownConstant(instructions, stackBase, depth, linked, instruction, tracking);
                            depth = size.depth();
                            sizes.add(0, size.value());
                        }
                        String descriptor = linked.owner().constantPool().className(instruction.operandA());
                        char leafDescriptor = descriptor.charAt(descriptor.length() - 1);
                        ArrayElementType leafType = ArrayElementType.fromDescriptor(leafDescriptor)
                                .orElseThrow(() -> new CompileException(linked.method().reference().displayName()
                                        + " at bytecode offset " + instruction.offset()
                                        + ": multidimensional object arrays are not supported yet: " + descriptor));
                        Value handle = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.NewMultiArray(handle, leafType, List.copyOf(sizes)));
                        tracking.markKnownArray(handle, sizes.get(0));
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
        return IrMethod.withInferredValues(linked.method().reference(), linked.method().isStatic(),
                stackBase + linked.method().maxStack(),
                nextValueId, List.copyOf(arrayDeclarations), List.copyOf(blocks));
    }

    private Set<Integer> arrayParameterSlots(Descriptor methodDescriptor, boolean isStatic) {
        Set<Integer> slots = new HashSet<>();
        int slot = isStatic ? 0 : 1;
        for (String parameter : methodDescriptor.parameters()) {
            if (Descriptor.isArrayType(parameter)) {
                slots.add(slot);
            }
            slot += Descriptor.jvmSlots(parameter);
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
            if ((newArrayInstruction.opcode() == 188
                    && ArrayElementType.fromAtype(newArrayInstruction.operandA()).isPresent())
                    || newArrayInstruction.opcode() == 189) {
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
                                       LinkedMethod linked, Instruction site, ValueTracking tracking) {
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
            case Terminator.Switch switched -> {
                List<Integer> targets = new ArrayList<>(switched.targets());
                targets.add(switched.defaultTarget());
                yield List.copyOf(targets);
            }
        };
    }

    private int stackDelta(LinkedMethod linked, Instruction instruction) {
        int opcode = instruction.opcode();
        return switch (opcode) {
            case 0, 132, 134, 138, 139, 143, 145, 146, 147, 116, 117, 118, 119, 167, 177, 188,
                    189, 190 -> 0;
            case 1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 13, 16, 17, 18, 19, 21, 23, 25,
                    26, 27, 28, 29, 34, 35, 36, 37, 42, 43, 44, 45, 89 -> 1;
            case 133, 135, 140, 141, 187 -> 1;
            case 9, 10, 14, 15, 20, 22, 24, 30, 31, 32, 33, 38, 39, 40, 41 -> 2;
            case 54, 56, 58, 59, 60, 61, 62, 67, 68, 69, 70, 75, 76, 77, 78,
                    87, 153, 154, 155, 156, 157, 158, 170, 171, 172, 174, 176, 191, 198, 199 -> -1;
            case 46, 48, 50, 51, 52, 53 -> -1;
            case 47, 49 -> 0;
            case 96, 98, 100, 102, 104, 106, 108, 110, 112, 114, 120, 122, 124, 126, 128, 130,
                    149, 150 -> -1;
            case 121, 123, 125, 136, 137, 142, 144 -> -1;
            case 55, 57, 63, 64, 65, 66, 71, 72, 73, 74, 88, 97, 99, 101, 103, 105, 107, 109, 111,
                    113, 115, 127, 129, 131, 173, 175 -> -2;
            case 159, 160, 161, 162, 163, 164, 165, 166 -> -2;
            case 148, 151, 152 -> -3;
            case 79, 81, 83, 84, 85, 86 -> -3;
            case 80, 82 -> -4;
            case 178, 179 -> {
                FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
                int slots = Descriptor.jvmSlots(field.descriptor());
                yield opcode == 178 ? slots : -slots;
            }
            case 180, 181 -> {
                FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
                int slots = Descriptor.jvmSlots(field.descriptor());
                yield opcode == 180 ? slots - 1 : -slots - 1;
            }
            case 197 -> 1 - instruction.operandB();
            case 182, 183, 184 -> {
                MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
                Descriptor descriptor = Descriptor.parse(called.descriptor());
                int consumed = descriptor.parameters().stream().mapToInt(Descriptor::jvmSlots).sum()
                        + (opcode == 182 || opcode == 183 ? 1 : 0);
                int produced = Descriptor.jvmSlots(descriptor.returnType());
                yield produced - consumed;
            }
            default -> throw new CompileException("Juno IR lowering does not support opcode " + opcode);
        };
    }

    private Lowered lowerCall(LinkedMethod linked, Instruction instruction, List<IrInstruction> instructions,
                               int stackBase, int depth, int nextValueId, ValueTracking tracking) {
        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        Optional<Intrinsic> intrinsic = IntrinsicRegistry.resolve(called);
        Value[] arguments = new Value[descriptor.parameters().size()];
        String[] literalStrings = new String[descriptor.parameters().size()];
        for (int index = arguments.length - 1; index >= 0; index--) {
            String parameterType = descriptor.parameters().get(index);
            depth -= Descriptor.jvmSlots(parameterType);
            if (Descriptor.isLong(parameterType)) {
                WidePopped popped = popWide(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = popped.nextValueId();
                Value packed = Value.int64(nextValueId++);
                instructions.add(new IrInstruction.PackLong(packed, popped.low(), popped.high()));
                arguments[index] = packed;
            } else if (Descriptor.isString(parameterType)) {
                Popped popped = pop(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = popped.nextValueId();
                if (intrinsic.isPresent()) {
                    String literal = tracking.knownString(popped.value());
                    if (literal == null) {
                        throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                                + instruction.offset() + ": " + called.displayName() + " requires a compile-time "
                                + "string literal argument");
                    }
                    literalStrings[index] = literal;
                } else {
                    arguments[index] = popped.value();
                }
            } else {
                Popped popped = Descriptor.isDouble(parameterType)
                        ? popDouble(instructions, stackBase, depth, nextValueId, tracking)
                        : Descriptor.isFloat(parameterType)
                                ? popFloat(instructions, stackBase, depth, nextValueId, tracking)
                                : pop(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = popped.nextValueId();
                arguments[index] = popped.value();
            }
        }
        List<Value> numericArguments = new ArrayList<>();
        List<String> literalArguments = new ArrayList<>();
        for (int index = 0; index < arguments.length; index++) {
            if (literalStrings[index] != null) {
                literalArguments.add(literalStrings[index]);
            } else {
                numericArguments.add(arguments[index]);
            }
        }
        Optional<Value> receiver = Optional.empty();
        if (instruction.opcode() == 182 || instruction.opcode() == 183) {
            Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
            nextValueId = popped.nextValueId();
            receiver = Optional.of(popped.value());
        }

        Value target = null;
        if (!descriptor.returnsVoid()) {
            target = Descriptor.isLong(descriptor.returnType())
                    ? Value.int64(nextValueId++)
                    : Descriptor.isDouble(descriptor.returnType())
                    ? Value.float64(nextValueId++)
                    : Descriptor.isFloat(descriptor.returnType())
                            ? Value.float32(nextValueId++)
                            : Value.int32(nextValueId++);
        }

        if (intrinsic.isPresent()) {
            instructions.add(new IrInstruction.IntrinsicCall(
                    Optional.ofNullable(target), intrinsic.get(), receiver, numericArguments, literalArguments));
        } else {
            List<Value> callArguments = new ArrayList<>();
            receiver.ifPresent(callArguments::add);
            callArguments.addAll(numericArguments);
            instructions.add(new IrInstruction.Call(Optional.ofNullable(target), called, List.copyOf(callArguments)));
        }
        if (target != null) {
            if (target.type() == JunoType.INT64) {
                Value low = Value.int32(nextValueId++);
                Value high = Value.int32(nextValueId++);
                instructions.add(new IrInstruction.UnpackLong(low, high, target));
                storeWideToStack(instructions, stackBase, depth, low, high, tracking);
            } else if (target.type() == JunoType.FLOAT64) {
                storeDoubleToStack(instructions, stackBase, depth, target, tracking);
            } else {
                storeToStack(instructions, stackBase, depth, target, tracking);
            }
            depth += target.type().jvmSlots();
        }
        return new Lowered(nextValueId, depth);
    }

    private Lowered lowerArrayLoad(List<IrInstruction> instructions, int stackBase, int depth,
                                    int nextValueId, ValueTracking tracking, ArrayElementType elementType) {
        Popped index = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = index.nextValueId();
        Popped array = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = array.nextValueId();
        Integer knownLength = tracking.knownLength(array.value());
        if (knownLength != null) {
            instructions.add(new IrInstruction.BoundsCheck(index.value(), knownLength));
        }
        Value target = new Value(nextValueId++, elementType.valueType());
        instructions.add(new IrInstruction.ArrayLoad(target, elementType, array.value(), index.value()));
        if (target.type() == JunoType.INT64) {
            Value low = Value.int32(nextValueId++);
            Value high = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.UnpackLong(low, high, target));
            storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        } else if (target.type() == JunoType.FLOAT64) {
            storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        } else {
            storeToStack(instructions, stackBase, depth, target, tracking);
        }
        depth += target.type().jvmSlots();
        return new Lowered(nextValueId, depth);
    }

    private Lowered lowerArrayStore(List<IrInstruction> instructions, int stackBase, int depth,
                                     int nextValueId, ValueTracking tracking, ArrayElementType elementType) {
        depth -= elementType.valueType().jvmSlots();
        Value storedValue;
        if (elementType.valueType() == JunoType.INT64) {
            WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
            nextValueId = value.nextValueId();
            storedValue = Value.int64(nextValueId++);
            instructions.add(new IrInstruction.PackLong(storedValue, value.low(), value.high()));
        } else {
            Popped value = switch (elementType.valueType()) {
                case INT32 -> pop(instructions, stackBase, depth, nextValueId, tracking);
                case FLOAT32 -> popFloat(instructions, stackBase, depth, nextValueId, tracking);
                case FLOAT64 -> popDouble(instructions, stackBase, depth, nextValueId, tracking);
                case INT64 -> throw new IllegalStateException("handled above");
            };
            nextValueId = value.nextValueId();
            storedValue = value.value();
        }
        Popped index = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = index.nextValueId();
        Popped array = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = array.nextValueId();
        Integer knownLength = tracking.knownLength(array.value());
        if (knownLength != null) {
            instructions.add(new IrInstruction.BoundsCheck(index.value(), knownLength));
        }
        instructions.add(new IrInstruction.ArrayStore(elementType, array.value(), index.value(), storedValue));
        return new Lowered(nextValueId, depth);
    }

    private Lowered lowerFieldLoad(LinkedMethod linked, Instruction instruction,
                                   List<IrInstruction> instructions, int stackBase, int depth,
                                   int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes) {
        FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
        JunoType type = validateInstanceField(linked, instruction, field, classes);
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        Value target = new Value(nextValueId++, type);
        List<Integer> enumFieldValues = resolveEnumIntegerFieldValues(field, classes);
        if (enumFieldValues != null) {
            Value values = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.IntArrayConst(values, enumFieldValues));
            instructions.add(new IrInstruction.BoundsCheck(receiver.value(), enumFieldValues.size()));
            instructions.add(new IrInstruction.ArrayLoad(target, ArrayElementType.INT, values, receiver.value()));
        } else {
            instructions.add(new IrInstruction.LoadField(target, field, receiver.value()));
        }
        if (type == JunoType.INT64) {
            Value low = Value.int32(nextValueId++);
            Value high = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.UnpackLong(low, high, target));
            storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        } else if (type == JunoType.FLOAT64) {
            storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        } else {
            storeToStack(instructions, stackBase, depth, target, tracking);
        }
        return new Lowered(nextValueId, depth + type.jvmSlots());
    }

    private Lowered lowerFieldStore(LinkedMethod linked, Instruction instruction,
                                    List<IrInstruction> instructions, int stackBase, int depth,
                                    int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes) {
        FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
        JunoType type = validateInstanceField(linked, instruction, field, classes);
        depth -= type.jvmSlots();
        Value stored;
        if (type == JunoType.INT64) {
            WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
            nextValueId = value.nextValueId();
            stored = Value.int64(nextValueId++);
            instructions.add(new IrInstruction.PackLong(stored, value.low(), value.high()));
        } else {
            Popped value = type == JunoType.FLOAT64
                    ? popDouble(instructions, stackBase, depth, nextValueId, tracking)
                    : type == JunoType.FLOAT32
                            ? popFloat(instructions, stackBase, depth, nextValueId, tracking)
                            : pop(instructions, stackBase, depth, nextValueId, tracking);
            nextValueId = value.nextValueId();
            stored = value.value();
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        instructions.add(new IrInstruction.StoreField(field, receiver.value(), stored));
        return new Lowered(nextValueId, depth);
    }

    private Lowered lowerEnumOrdinal(List<IrInstruction> instructions, int stackBase, int depth,
                                     int nextValueId, ValueTracking tracking) {
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        storeToStack(instructions, stackBase, depth, receiver.value(), tracking);
        return new Lowered(nextValueId, depth + 1);
    }

    private Lowered lowerEnumValues(MethodRef called, List<IrInstruction> instructions, int stackBase, int depth,
                                    int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes) {
        JavaClass enumClass = classes.get(called.owner());
        List<Integer> ordinals = new ArrayList<>();
        for (int index = 0; index < enumClass.enumConstantNames().size(); index++) {
            ordinals.add(index);
        }
        Value target = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntArrayConst(target, List.copyOf(ordinals)));
        tracking.markKnownArray(target, ordinals.size());
        storeToStack(instructions, stackBase, depth, target, tracking);
        return new Lowered(nextValueId, depth + 1);
    }

    private Lowered discardInstanceCall(MethodRef called, List<IrInstruction> instructions, int stackBase,
                                         int depth, int nextValueId, ValueTracking tracking) {
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        for (int index = descriptor.parameters().size() - 1; index >= 0; index--) {
            int slots = Descriptor.jvmSlots(descriptor.parameters().get(index));
            depth -= slots;
            if (slots == 2) {
                WidePopped ignored = popWide(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = ignored.nextValueId();
            } else {
                Popped ignored = pop(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = ignored.nextValueId();
            }
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        return new Lowered(receiver.nextValueId(), depth);
    }

    private boolean isRuntimeBaseConstructor(MethodRef called) {
        return called.name().equals("<init>")
                && called.owner().startsWith("java/lang/");
    }

    private boolean isStringBuilderConstruction(MethodRef called) {
        return called.owner().equals("java/lang/StringBuilder") && called.name().equals("<init>")
                && called.descriptor().equals("(I)V");
    }

    /**
     * {@code new StringBuilder(capacity)} — {@code new} (opcode 187) already pushed a placeholder
     * {@code 0} for any unrecognized {@code java/lang/*} allocation (see that opcode's handling
     * above), which {@code dup} then duplicated: one copy is consumed here as this constructor's
     * receiver, the other survives on the stack as the expression's result. Since {@code <init>}
     * is declared {@code void}, the normal call-lowering "push a return value" path never runs, so
     * the surviving placeholder would otherwise stay {@code 0} forever — this overwrites that
     * exact stack slot with the real arena-allocated handle instead.
     */
    private Lowered lowerStringBuilderConstruction(List<IrInstruction> instructions, int stackBase, int depth,
                                                     int nextValueId, ValueTracking tracking) {
        depth -= 1;
        Popped capacity = pop(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = capacity.nextValueId();
        depth -= 1;
        Popped discardedReceiver = pop(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = discardedReceiver.nextValueId();
        Value handle = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntrinsicCall(Optional.of(handle), Intrinsic.STRING_BUILDER_NEW,
                Optional.empty(), List.of(capacity.value()), List.of()));
        storeToStack(instructions, stackBase, depth - 1, handle, tracking);
        return new Lowered(nextValueId, depth);
    }

    private boolean isEnumOrdinal(Map<String, JavaClass> classes, MethodRef called) {
        JavaClass owner = classes.get(called.owner());
        return called.name().equals("ordinal") && called.descriptor().equals("()I")
                && ((owner != null && owner.isEnum()) || called.owner().equals("java/lang/Enum"));
    }

    private boolean isEnumValues(Map<String, JavaClass> classes, MethodRef called) {
        JavaClass owner = classes.get(called.owner());
        return owner != null && owner.isEnum() && called.name().equals("values")
                && called.descriptor().startsWith("()[L");
    }

    private boolean isCompileTimeGetenv(MethodRef called) {
        return called.owner().equals("java/lang/System") && called.name().equals("getenv")
                && called.descriptor().equals("(Ljava/lang/String;)Ljava/lang/String;");
    }

    /**
     * {@code System.getenv("NAME")} of a literal environment-variable name is evaluated by Juno itself,
     * at compile time, by calling the real {@code System.getenv} in Juno's own JVM process — reading
     * Juno's build-time environment (wherever {@code juno compile} runs), never the target device's.
     * The result becomes a compile-time string literal exactly like {@code ldc "..."} (see
     * {@link #pushStringConst}), used e.g. to keep WiFi credentials out of committed source.
     */
    private Lowered lowerCompileTimeGetenv(LinkedMethod linked, Instruction instruction,
                                            List<IrInstruction> instructions, int stackBase, int depth,
                                            int nextValueId, ValueTracking tracking) {
        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = popped.nextValueId();
        String variableName = tracking.knownString(popped.value());
        if (variableName == null) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": System.getenv requires a compile-time string literal "
                    + "environment-variable name");
        }
        String value = System.getenv(variableName);
        if (value == null) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": environment variable " + variableName + " is not set; Juno "
                    + "resolves System.getenv(...) at compile time, so it must be set wherever `juno compile` runs");
        }
        nextValueId = pushStringConst(instructions, stackBase, depth, nextValueId, value, tracking);
        depth++;
        return new Lowered(nextValueId, depth);
    }

    private boolean isDrawTextCall(MethodRef called) {
        return called.equals(DRAW_TEXT_METHOD);
    }

    /**
     * {@code LedCanvas.drawText(frame, "literal", x, y)} is unrolled entirely at compile time into one
     * {@code LedCanvas.drawChar} call per character of the literal, each character's x position
     * computed as {@code x + i * (LedMatrixFontAscii.GLYPH_WIDTH + 1)} — exactly what writing the
     * calls out by hand (as {@code LedMatrixScrollingText} used to) would produce. {@code drawChar} is
     * an entirely ordinary reachable Juno method (see {@link io.github.jabrena.juno.linker.Linker}'s
     * matching reachability special-case), so neither backend needs any new codegen for this at all.
     */
    private Lowered lowerDrawText(LinkedMethod linked, Instruction instruction, List<IrInstruction> instructions,
                                   int stackBase, int depth, int nextValueId, ValueTracking tracking) {
        Popped originY = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = originY.nextValueId();
        Popped originX = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = originX.nextValueId();
        Popped text = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = text.nextValueId();
        String message = tracking.knownString(text.value());
        if (message == null) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": " + DRAW_TEXT_METHOD.displayName() + " requires a compile-time "
                    + "string literal text argument (Juno has no heap for a runtime String value)");
        }
        Popped frame = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = frame.nextValueId();

        for (int index = 0; index < message.length(); index++) {
            Value charValue = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.Const(charValue, message.charAt(index)));
            Value xValue = originX.value();
            if (index > 0) {
                Value columnOffset = Value.int32(nextValueId++);
                instructions.add(new IrInstruction.Const(columnOffset, index * DRAW_TEXT_CHAR_SPACING));
                xValue = Value.int32(nextValueId++);
                instructions.add(new IrInstruction.Binary(xValue, BinaryOp.ADD, originX.value(), columnOffset));
            }
            instructions.add(new IrInstruction.Call(Optional.empty(), DRAW_CHAR_METHOD,
                    List.of(frame.value(), charValue, xValue, originY.value())));
        }
        return new Lowered(nextValueId, depth);
    }

    /**
     * {@code new X} + {@code dup} + args + {@code invokespecial <init>} is the only object-construction
     * pattern Juno supports, and only for a validated simple record (see {@link #validateSimpleRecord}):
     * there is no heap, so a record is never actually allocated, just decomposed into its N argument
     * values. {@code dup} already duplicated the {@code new}-pushed placeholder (see the opcode 89 case) —
     * this pops the copy consumed as the receiver, then tags the slot the OTHER (surviving) copy occupies,
     * exactly mirroring {@link #lowerCall}'s pop order for an instance call.
     */
    private Lowered lowerRecordConstruction(LinkedMethod linked, Instruction instruction, MethodRef called,
                                             List<IrInstruction> instructions, int stackBase, int depth,
                                             int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes) {
        if (!called.name().equals("<init>")) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": invokespecial is only supported for constructing a recognized "
                    + "record (" + called.displayName() + " is not a constructor call this can resolve)");
        }
        JavaClass recordClass = classes.get(called.owner());
        if (recordClass == null || !recordClass.isRecord()) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": object construction is only supported for simple records ("
                    + called.owner().replace('/', '.') + " is not one); general objects/constructors are "
                    + "not supported");
        }
        List<FieldInfo> components = validateSimpleRecord(recordClass);
        Value[] fieldValues = new Value[components.size()];
        for (int index = fieldValues.length - 1; index >= 0; index--) {
            Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
            nextValueId = popped.nextValueId();
            fieldValues[index] = popped.value();
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        // depth now reflects "1 item left on the stack" (the dup'd copy that survives, per the class-level
        // docs above); its slot is stackBase + depth - 1, matching pop()'s own "depth after popping" convention.
        tracking.markStackSlotRecord(stackBase + depth - 1, new RecordInstance(recordClass.name(), List.of(fieldValues)));
        return new Lowered(nextValueId, depth);
    }

    /**
     * A record accessor call ({@code p.x()}) never actually calls anything: it resolves directly to the
     * field value captured at construction time (see {@link #lowerRecordConstruction}), reusing that
     * existing {@link Value} rather than emitting any new instruction.
     */
    private Lowered lowerRecordAccessor(LinkedMethod linked, Instruction instruction, MethodRef called,
                                         List<IrInstruction> instructions, int stackBase, int depth,
                                         int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes) {
        JavaClass recordClass = classes.get(called.owner());
        List<FieldInfo> components = validateSimpleRecord(recordClass);
        int componentIndex = -1;
        for (int index = 0; index < components.size(); index++) {
            if (components.get(index).name().equals(called.name())) {
                componentIndex = index;
                break;
            }
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        RecordInstance instance = tracking.knownRecord(receiver.value());
        if (instance == null || !instance.className().equals(recordClass.name()) || componentIndex < 0) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": " + called.displayName() + " can only be called on a record "
                    + "constructed directly in this method and assigned to a local exactly once "
                    + "(\"effectively final\"); this receiver's construction site could not be resolved at "
                    + "compile time");
        }
        Value fieldValue = instance.fieldValues().get(componentIndex);
        storeToStack(instructions, stackBase, depth, fieldValue, tracking);
        depth++;
        return new Lowered(nextValueId, depth);
    }

    /**
     * Validates that {@code recordClass} is a "simple" record Juno can safely decompose: every component is
     * an int-like primitive, its canonical constructor is exactly the compiler-generated shape (no compact
     * or custom constructor logic), and every accessor is exactly the compiler-generated trivial getter (no
     * override). Anything else risks silently using a raw constructor argument or field value where the
     * user's own code would have transformed it — a compile error here instead. Cached per class per
     * compile, since bytecode decoding is not free and the same record can be constructed many times.
     */
    private List<FieldInfo> validateSimpleRecord(JavaClass recordClass) {
        List<FieldInfo> cached = validatedRecords.get(recordClass.name());
        if (cached != null) {
            return cached;
        }
        List<FieldInfo> components = recordClass.recordComponents();
        for (FieldInfo component : components) {
            if (!Descriptor.isIntegerLike(component.descriptor())) {
                throw new CompileException("Record " + recordClass.name().replace('/', '.') + " has a component '"
                        + component.name() + "' of unsupported type " + component.descriptor() + "; Juno's "
                        + "record support is limited to boolean/byte/char/short/int components");
            }
        }
        String initDescriptor = "(" + components.stream().map(FieldInfo::descriptor).collect(Collectors.joining()) + ")V";
        JavaMethod init = recordClass.findMethod("<init>", initDescriptor);
        if (init == null || init.code() == null) {
            throw new CompileException("Record " + recordClass.name().replace('/', '.')
                    + " has no matching canonical constructor");
        }
        validateCanonicalConstructor(recordClass, init, components);
        for (FieldInfo component : components) {
            String accessorDescriptor = "()" + component.descriptor();
            JavaMethod accessor = recordClass.findMethod(component.name(), accessorDescriptor);
            if (accessor == null || accessor.code() == null) {
                throw new CompileException("Record " + recordClass.name().replace('/', '.') + " has no accessor "
                        + "method for component '" + component.name() + "'");
            }
            validateTrivialAccessor(recordClass, accessor, component);
        }
        validatedRecords.put(recordClass.name(), components);
        return components;
    }

    /** Expected shape: {@code aload_0; invokespecial <super ctor>; (aload_0; iload_N; putfield)*; return}. */
    private void validateCanonicalConstructor(JavaClass recordClass, JavaMethod init, List<FieldInfo> components) {
        List<Instruction> instructions = decoder.decode(init);
        int expectedCount = 2 + 3 * components.size() + 1;
        if (instructions.size() != expectedCount
                || instructions.get(0).opcode() != 42
                || instructions.get(1).opcode() != 183
                || instructions.get(instructions.size() - 1).opcode() != 177) {
            throw unsupportedConstructor(recordClass);
        }
        for (int index = 0; index < components.size(); index++) {
            Instruction loadThis = instructions.get(2 + 3 * index);
            Instruction loadArg = instructions.get(2 + 3 * index + 1);
            Instruction store = instructions.get(2 + 3 * index + 2);
            Integer argSlot = intLoadSlot(loadArg);
            if (loadThis.opcode() != 42 || store.opcode() != 181 || argSlot == null || argSlot != index + 1) {
                throw unsupportedConstructor(recordClass);
            }
            FieldRef field = recordClass.constantPool().fieldRef(store.operandA());
            if (!field.owner().equals(recordClass.name()) || !field.name().equals(components.get(index).name())) {
                throw unsupportedConstructor(recordClass);
            }
        }
    }

    /** Expected shape: {@code aload_0; getfield <this component>; ireturn}. */
    private void validateTrivialAccessor(JavaClass recordClass, JavaMethod accessor, FieldInfo component) {
        List<Instruction> instructions = decoder.decode(accessor);
        FieldRef field = instructions.size() == 3 && instructions.get(1).opcode() == 180
                ? recordClass.constantPool().fieldRef(instructions.get(1).operandA()) : null;
        if (instructions.size() != 3
                || instructions.get(0).opcode() != 42
                || instructions.get(1).opcode() != 180
                || instructions.get(2).opcode() != 172
                || field == null
                || !field.owner().equals(recordClass.name())
                || !field.name().equals(component.name())) {
            throw new CompileException("Record " + recordClass.name().replace('/', '.') + "." + component.name()
                    + "() has a custom body; Juno's record support requires the plain compiler-generated "
                    + "accessor (just returning the field)");
        }
    }

    private CompileException unsupportedConstructor(JavaClass recordClass) {
        return new CompileException("Record " + recordClass.name().replace('/', '.') + " has a custom or compact "
                + "constructor body; Juno's record support requires the plain compiler-generated canonical "
                + "constructor (no extra validation/transformation logic)");
    }

    /** The local slot an {@code iload}/{@code iload_0..3} instruction reads, or {@code null} otherwise. */
    private Integer intLoadSlot(Instruction instruction) {
        return switch (instruction.opcode()) {
            case 21 -> instruction.operandA();
            case 26, 27, 28, 29 -> instruction.opcode() - 26;
            default -> null;
        };
    }

    /** Every JVM local slot assigned via {@code astore} exactly once in the whole method (see {@link #astoreSlot}). */
    private Set<Integer> computeSingleAssignmentLocals(LinkedMethod linked) {
        Map<Integer, Integer> storeCounts = new HashMap<>();
        for (Instruction instruction : linked.instructions()) {
            Integer slot = astoreSlot(instruction);
            if (slot != null) {
                storeCounts.merge(slot, 1, Integer::sum);
            }
        }
        Set<Integer> result = new HashSet<>();
        for (Map.Entry<Integer, Integer> entry : storeCounts.entrySet()) {
            if (entry.getValue() == 1) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * If {@code slot} is assigned exactly once in the whole method and the value being stored is a known
     * record instance, remembers that fact for {@code slot}'s entire remaining lifetime — safe because Java
     * requires definite assignment before any read, so a single-assignment slot can only ever hold that one
     * value (the same "effectively final" reasoning already used for arrays).
     */
    private void trackRecordLocalIfSingleAssignment(int slot, Value value, ValueTracking tracking,
                                                     Set<Integer> singleAssignmentLocals,
                                                     Map<Integer, RecordInstance> slotRecordInstance) {
        if (singleAssignmentLocals.contains(slot)) {
            RecordInstance instance = tracking.knownRecord(value);
            if (instance != null) {
                slotRecordInstance.put(slot, instance);
            }
        }
    }

    /** Same reasoning as {@link #trackRecordLocalIfSingleAssignment}, but for a known string literal. */
    private void trackStringLocalIfSingleAssignment(int slot, Value value, ValueTracking tracking,
                                                     Set<Integer> singleAssignmentLocals,
                                                     Map<Integer, String> slotStringInstance) {
        if (singleAssignmentLocals.contains(slot)) {
            String literal = tracking.knownString(value);
            if (literal != null) {
                slotStringInstance.put(slot, literal);
            }
        }
    }

    private int pushConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int value,
                           ValueTracking tracking) {
        Value target = Value.int32(nextValueId);
        instructions.add(new IrInstruction.Const(target, value));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        tracking.clearStackSlot(stackBase + depth);
        return nextValueId + 1;
    }

    /**
     * A string literal points directly at immutable generated storage while its text remains tracked at
     * compile time for intrinsics that require literal arguments.
     */
    private int pushStringConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                 String value, ValueTracking tracking) {
        Value target = Value.int32(nextValueId);
        instructions.add(new IrInstruction.StringConst(target, value));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        tracking.markKnownString(target, value);
        tracking.markStackSlotString(stackBase + depth, value);
        return nextValueId + 1;
    }

    private int pushFloatConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                               float value, ValueTracking tracking) {
        Value target = Value.float32(nextValueId);
        instructions.add(new IrInstruction.FloatConst(target, value));
        storeToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    private int pushDoubleConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                double value, ValueTracking tracking) {
        Value target = Value.float64(nextValueId);
        instructions.add(new IrInstruction.DoubleConst(target, value));
        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    private int pushLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int local,
                          Map<Integer, Integer> slotArrayLength, Set<Integer> arrayParameterSlots,
                          Map<Integer, RecordInstance> slotRecordInstance, Map<Integer, String> slotStringInstance,
                          ValueTracking tracking) {
        Value target = Value.int32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(target, local));
        Integer knownLength = slotArrayLength.get(local);
        if (knownLength != null) {
            tracking.markKnownArray(target, knownLength);
        }
        if (arrayParameterSlots.contains(local)) {
            tracking.markParameterForward(target);
        }
        RecordInstance knownRecord = slotRecordInstance.get(local);
        if (knownRecord != null) {
            tracking.markKnownRecord(target, knownRecord);
        }
        String knownString = slotStringInstance.get(local);
        if (knownString != null) {
            tracking.markKnownString(target, knownString);
        }
        storeToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    private int pushFloatLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                              int local, ValueTracking tracking) {
        Value target = Value.float32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(target, local));
        storeToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    private int pushDoubleLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                               int local, ValueTracking tracking) {
        Value target = Value.float64(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(target, local));
        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    private int pushBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                            BinaryOp operation, ValueTracking tracking) {
        int depth = depthBeforePush;
        Popped right = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = right.nextValueId();
        Popped left = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = left.nextValueId();
        Value target = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.Binary(target, operation, left.value(), right.value()));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        tracking.clearStackSlot(stackBase + depth);
        return nextValueId;
    }

    private int pushFloatBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush,
                                int nextValueId, FloatBinaryOp operation, ValueTracking tracking) {
        int depth = depthBeforePush;
        Popped right = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = right.nextValueId();
        Popped left = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = left.nextValueId();
        Value target = Value.float32(nextValueId++);
        instructions.add(new IrInstruction.FloatBinary(target, operation, left.value(), right.value()));
        storeToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId;
    }

    private int pushDoubleBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush,
                                 int nextValueId, FloatBinaryOp operation, ValueTracking tracking) {
        int depth = depthBeforePush - 2;
        Popped right = popDouble(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = right.nextValueId();
        depth -= 2;
        Popped left = popDouble(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = left.nextValueId();
        Value target = Value.float64(nextValueId++);
        instructions.add(new IrInstruction.DoubleBinary(target, operation, left.value(), right.value()));
        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId;
    }

    private int pushUnary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                           UnaryOp operation, ValueTracking tracking) {
        int depth = depthBeforePush - 1;
        Popped operand = pop(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = operand.nextValueId();
        Value target = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.Unary(target, operation, operand.value()));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        tracking.clearStackSlot(stackBase + depth);
        return nextValueId;
    }

    private int pushFloatNegate(List<IrInstruction> instructions, int stackBase, int depthBeforePush,
                                int nextValueId, ValueTracking tracking) {
        int depth = depthBeforePush - 1;
        Popped operand = popFloat(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = operand.nextValueId();
        Value target = Value.float32(nextValueId++);
        instructions.add(new IrInstruction.FloatNegate(target, operand.value()));
        storeToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId;
    }

    private int pushDoubleNegate(List<IrInstruction> instructions, int stackBase, int depthBeforePush,
                                 int nextValueId, ValueTracking tracking) {
        int depth = depthBeforePush - 2;
        Popped operand = popDouble(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = operand.nextValueId();
        Value target = Value.float64(nextValueId++);
        instructions.add(new IrInstruction.DoubleNegate(target, operand.value()));
        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId;
    }

    /** Emits a StoreLocal to a stack slot and keeps {@code tracking} consistent with it. */
    private void storeToStack(List<IrInstruction> instructions, int stackBase, int depth, Value value, ValueTracking tracking) {
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, value));
        tracking.recordPush(stackBase + depth, value);
    }

    private Popped pop(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId, ValueTracking tracking) {
        Value value = Value.int32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(value, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, value);
        return new Popped(value, nextValueId + 1);
    }

    private Popped popFloat(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId,
                            ValueTracking tracking) {
        Value value = Value.float32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(value, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, value);
        return new Popped(value, nextValueId + 1);
    }

    private Popped popDouble(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId,
                             ValueTracking tracking) {
        Value value = Value.float64(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(value, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, value);
        tracking.clearStackSlot(stackBase + depthAfterPop + 1);
        return new Popped(value, nextValueId + 1);
    }

    private void storeDoubleToStack(List<IrInstruction> instructions, int stackBase, int depth, Value value,
                                    ValueTracking tracking) {
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, value));
        tracking.recordPush(stackBase + depth, value);
        tracking.clearStackSlot(stackBase + depth + 1);
    }

    /**
     * A {@code long} occupies two consecutive stack/local slots; by convention the lower-depth (lower-index)
     * slot holds the low 32 bits and the next one holds the high 32 bits, both for synthetic stack slots and
     * for JVM local slots (e.g. {@code lload n} reads locals {@code n} and {@code n + 1}).
     */
    private WidePopped popWide(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId, ValueTracking tracking) {
        Value low = Value.int32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(low, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, low);
        Value high = Value.int32(nextValueId + 1);
        instructions.add(new IrInstruction.LoadLocal(high, stackBase + depthAfterPop + 1));
        tracking.recordPop(stackBase + depthAfterPop + 1, high);
        return new WidePopped(low, high, nextValueId + 2);
    }

    /** Stores a long's two halves to a pair of stack slots; arrays are never wide, so both slots are defensively cleared. */
    private void storeWideToStack(List<IrInstruction> instructions, int stackBase, int depth, Value low, Value high, ValueTracking tracking) {
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, low));
        tracking.clearStackSlot(stackBase + depth);
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth + 1, high));
        tracking.clearStackSlot(stackBase + depth + 1);
    }

    private int pushWideConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, long value,
                               ValueTracking tracking) {
        Value low = Value.int32(nextValueId);
        Value high = Value.int32(nextValueId + 1);
        instructions.add(new IrInstruction.LongConst(low, high, value));
        storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        return nextValueId + 2;
    }

    private int pushWideLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int local,
                              ValueTracking tracking) {
        Value low = Value.int32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(low, local));
        Value high = Value.int32(nextValueId + 1);
        instructions.add(new IrInstruction.LoadLocal(high, local + 1));
        storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        return nextValueId + 2;
    }

    private int pushLongBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                                BinaryOp operation, ValueTracking tracking) {
        int depth = depthBeforePush - 2;
        WidePopped right = popWide(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = right.nextValueId();
        depth -= 2;
        WidePopped left = popWide(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = left.nextValueId();
        Value targetLow = Value.int32(nextValueId++);
        Value targetHigh = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.LongBinary(targetLow, targetHigh, operation,
                left.low(), left.high(), right.low(), right.high()));
        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
        return nextValueId;
    }

    /** {@code lshl}/{@code lshr}/{@code lushr}: the shift amount is a plain int, popped before the long value. */
    private int pushLongShift(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                               BinaryOp operation, ValueTracking tracking) {
        int depth = depthBeforePush;
        Popped amount = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = amount.nextValueId();
        depth -= 2;
        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = value.nextValueId();
        Value targetLow = Value.int32(nextValueId++);
        Value targetHigh = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.LongShift(targetLow, targetHigh, operation,
                value.low(), value.high(), amount.value()));
        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
        return nextValueId;
    }

    private int pushLongNegate(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
                                ValueTracking tracking) {
        int depth = depthBeforePush - 2;
        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = value.nextValueId();
        Value targetLow = Value.int32(nextValueId++);
        Value targetHigh = Value.int32(nextValueId++);
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

    /**
     * Resolves a {@code getstatic} target to an enum constant's ordinal. Juno never constructs a real enum
     * object; the field must belong to a class recognized as an enum (see {@link JavaClass#isEnum()}) and be
     * one of its constants ({@link JavaClass#enumConstantNames()}) — any other static field (mutable, or an
     * enum's own non-constant field, neither of which Juno supports) is a clear compile error.
     */
    private Integer resolveEnumOrdinal(FieldRef field, Map<String, JavaClass> classes) {
        JavaClass owner = classes.get(field.owner());
        int ordinal = owner == null || !owner.isEnum() ? -1 : owner.enumConstantNames().indexOf(field.name());
        return ordinal < 0 ? null : ordinal;
    }

    /**
     * Resolves an enum's single constructor-associated integer field to one value per ordinal.
     * {@code javac} represents {@code KEY(123)} as a literal constructor argument in {@code <clinit>}
     * and a direct parameter-to-field assignment in the enum constructor. Juno keeps enum values as
     * ordinal ints, so a field read becomes an immutable lookup indexed by that ordinal instead of an
     * object dereference.
     */
    private List<Integer> resolveEnumIntegerFieldValues(FieldRef field, Map<String, JavaClass> classes) {
        JavaClass enumClass = classes.get(field.owner());
        if (enumClass == null || !enumClass.isEnum()) {
            return null;
        }
        if (!Descriptor.isIntegerLike(field.descriptor())) {
            throw new CompileException("Enum associated values must use an integer-like primitive field: "
                    + field.displayName());
        }

        String constructorDescriptor = null;
        for (JavaMethod method : enumClass.methods()) {
            if (!method.name().equals("<init>")) {
                continue;
            }
            List<Instruction> constructor = decoder.decode(method);
            for (int index = 2; index < constructor.size(); index++) {
                Instruction store = constructor.get(index);
                if (store.opcode() != 181
                        || !enumClass.constantPool().fieldRef(store.operandA()).equals(field)) {
                    continue;
                }
                Instruction receiverLoad = constructor.get(index - 2);
                Instruction valueLoad = constructor.get(index - 1);
                Descriptor descriptor = Descriptor.parse(method.descriptor());
                if (receiverLoad.opcode() != 42
                        || integerLocalIndex(valueLoad) != 3
                        || !descriptor.parameters().equals(List.of("Ljava/lang/String;", "I", "I"))) {
                    throw new CompileException("Enum associated value must be assigned directly from its single "
                            + "integer constructor argument: " + field.displayName());
                }
                constructorDescriptor = method.descriptor();
            }
        }
        if (constructorDescriptor == null) {
            throw new CompileException("Cannot resolve enum constructor assignment for " + field.displayName());
        }

        JavaMethod initializer = enumClass.findMethod("<clinit>", "()V");
        if (initializer == null) {
            throw new CompileException("Enum has no initializer for associated values: " + field.displayName());
        }
        List<Instruction> bytecode = decoder.decode(initializer);
        List<String> constantNames = enumClass.enumConstantNames();
        Integer[] values = new Integer[constantNames.size()];
        for (int index = 2; index < bytecode.size(); index++) {
            Instruction store = bytecode.get(index);
            if (store.opcode() != 179) {
                continue;
            }
            FieldRef constant = enumClass.constantPool().fieldRef(store.operandA());
            int ordinal = constant.owner().equals(enumClass.name())
                    ? constantNames.indexOf(constant.name()) : -1;
            if (ordinal < 0) {
                continue;
            }
            Instruction constructorCall = bytecode.get(index - 1);
            MethodRef called = constructorCall.opcode() == 183
                    ? enumClass.constantPool().methodRef(constructorCall.operandA()) : null;
            if (called == null || !called.owner().equals(enumClass.name()) || !called.name().equals("<init>")
                    || !called.descriptor().equals(constructorDescriptor)) {
                throw new CompileException("Cannot resolve enum constant construction for " + constant.displayName());
            }
            Integer value = literalValue(bytecode.get(index - 2), enumClass);
            if (value == null) {
                throw new CompileException("Enum associated value must be a compile-time integer literal: "
                        + constant.displayName());
            }
            values[ordinal] = value;
        }
        List<Integer> resolved = new ArrayList<>(values.length);
        for (int ordinal = 0; ordinal < values.length; ordinal++) {
            if (values[ordinal] == null) {
                throw new CompileException("Cannot resolve associated value for enum constant "
                        + enumClass.name().replace('/', '.') + "." + constantNames.get(ordinal));
            }
            resolved.add(values[ordinal]);
        }
        return List.copyOf(resolved);
    }

    private int integerLocalIndex(Instruction instruction) {
        return switch (instruction.opcode()) {
            case 21 -> instruction.operandA();
            case 26, 27, 28, 29 -> instruction.opcode() - 26;
            default -> -1;
        };
    }

    private List<Integer> resolveEnumSwitchMap(FieldRef requested, Map<String, JavaClass> classes) {
        JavaClass mappingClass = classes.get(requested.owner());
        JavaMethod initializer = mappingClass == null ? null : mappingClass.findMethod("<clinit>", "()V");
        if (initializer == null) {
            throw new CompileException("Synthetic enum switch map has no initializer: " + requested.displayName());
        }
        List<Instruction> bytecode = decoder.decode(initializer);
        List<Integer> mapping = null;
        for (int index = 4; index < bytecode.size(); index++) {
            if (bytecode.get(index).opcode() != 79) {
                continue;
            }
            Instruction mapLoad = bytecode.get(index - 4);
            Instruction enumLoad = bytecode.get(index - 3);
            Instruction ordinalCall = bytecode.get(index - 2);
            Instruction valuePush = bytecode.get(index - 1);
            if (mapLoad.opcode() != 178 || enumLoad.opcode() != 178 || ordinalCall.opcode() != 182) {
                continue;
            }
            FieldRef mapField = mappingClass.constantPool().fieldRef(mapLoad.operandA());
            if (!mapField.equals(requested)) {
                continue;
            }
            FieldRef enumField = mappingClass.constantPool().fieldRef(enumLoad.operandA());
            JavaClass enumClass = classes.get(enumField.owner());
            int ordinal = enumClass == null ? -1 : enumClass.enumConstantNames().indexOf(enumField.name());
            Integer switchValue = literalValue(valuePush, mappingClass);
            if (ordinal < 0 || switchValue == null) {
                throw new CompileException("Cannot resolve synthetic enum switch entry for " + requested.displayName());
            }
            if (mapping == null) {
                mapping = new ArrayList<>();
                for (int item = 0; item < enumClass.enumConstantNames().size(); item++) {
                    mapping.add(0);
                }
            }
            mapping.set(ordinal, switchValue);
        }
        if (mapping == null) {
            throw new CompileException("Cannot resolve synthetic enum switch map: " + requested.displayName());
        }
        return List.copyOf(mapping);
    }

    private Integer literalValue(Instruction instruction, JavaClass owner) {
        return switch (instruction.opcode()) {
            case 2 -> -1;
            case 3, 4, 5, 6, 7, 8 -> instruction.opcode() - 3;
            case 16, 17 -> instruction.operandA();
            case 18, 19 -> owner.constantPool().integer(instruction.operandA());
            default -> null;
        };
    }

    private JunoType validateStaticField(LinkedMethod linked, Instruction instruction, FieldRef field,
                                         Map<String, JavaClass> classes) {
        JavaClass owner = classes.get(field.owner());
        FieldInfo declaration = owner == null ? null : owner.findField(field.name(), field.descriptor());
        String location = linked.method().reference().displayName() + " at bytecode offset " + instruction.offset();
        if (declaration == null || !declaration.isStatic()) {
            throw new CompileException(location + ": static field not found: " + field.displayName());
        }
        return typeOfDescriptor(location, field, classes);
    }

    private JunoType validateInstanceField(LinkedMethod linked, Instruction instruction, FieldRef field,
                                           Map<String, JavaClass> classes) {
        JavaClass owner = classes.get(field.owner());
        FieldInfo declaration = owner == null ? null : owner.findField(field.name(), field.descriptor());
        String location = linked.method().reference().displayName() + " at bytecode offset " + instruction.offset();
        if (declaration == null || declaration.isStatic()) {
            throw new CompileException(location + ": instance field not found: " + field.displayName());
        }
        return typeOfDescriptor(location, field, classes);
    }

    private JunoType typeOfDescriptor(String location, FieldRef field, Map<String, JavaClass> classes) {
        if (Descriptor.isIntegerLike(field.descriptor())) {
            return JunoType.INT32;
        }
        if (Descriptor.isLong(field.descriptor())) {
            return JunoType.INT64;
        }
        if (Descriptor.isFloat(field.descriptor())) {
            return JunoType.FLOAT32;
        }
        if (Descriptor.isDouble(field.descriptor())) {
            return JunoType.FLOAT64;
        }
        if (Descriptor.isArrayType(field.descriptor())
                || Descriptor.isReferenceType(field.descriptor(), classes.keySet())) {
            return JunoType.INT32;
        }
        throw new CompileException(location + ": unsupported field type: " + field.displayName());
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
     * Per-method bookkeeping of facts about values that a real type system would normally carry: whether a
     * value is a known-length local array (safe to bounds-check), a direct array-parameter forward (safe to
     * return), or a known record instance (its field values, so an accessor call can resolve directly to one
     * without ever needing a real object). Every "known X" map is keyed by {@link Value} and never reset —
     * values are single-assignment, so a fact about one is true for its whole lifetime. The stack-slot views
     * are reset at the start of every block (see the class-level docs for why).
     */
    private static final class ValueTracking {
        private final Map<Value, Integer> arrayLength = new HashMap<>();
        private final Set<Value> parameterForwarded = new HashSet<>();
        private final Map<Value, RecordInstance> recordOf = new HashMap<>();
        private final Map<Value, String> stringOf = new HashMap<>();
        private Map<Integer, Integer> currentStackSlotLength = new HashMap<>();
        private Set<Integer> currentStackSlotIsParameterForward = new HashSet<>();
        private Map<Integer, RecordInstance> currentStackSlotRecord = new HashMap<>();
        private Map<Integer, String> currentStackSlotString = new HashMap<>();
        private Map<Integer, JunoType> currentStackSlotType = new HashMap<>();

        void startBlock() {
            currentStackSlotLength = new HashMap<>();
            currentStackSlotIsParameterForward = new HashSet<>();
            currentStackSlotRecord = new HashMap<>();
            currentStackSlotString = new HashMap<>();
            currentStackSlotType = new HashMap<>();
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

        void markKnownRecord(Value value, RecordInstance instance) {
            recordOf.put(value, instance);
        }

        RecordInstance knownRecord(Value value) {
            return recordOf.get(value);
        }

        /**
         * Tags the value currently occupying {@code slot} (whichever it turns out to be once popped) as a
         * record instance, without needing a {@link Value} in hand — used right after {@code invokespecial
         * <init>} finishes, where the surviving {@code dup}'d reference is still on the stack, never re-read.
         */
        void markStackSlotRecord(int slot, RecordInstance instance) {
            currentStackSlotRecord.put(slot, instance);
        }

        void markKnownString(Value value, String literal) {
            stringOf.put(value, literal);
        }

        String knownString(Value value) {
            return stringOf.get(value);
        }

        /** Same reasoning as {@link #markStackSlotRecord}, but for a compile-time string literal. */
        void markStackSlotString(int slot, String literal) {
            currentStackSlotString.put(slot, literal);
        }

        void clearStackSlot(int slot) {
            currentStackSlotLength.remove(slot);
            currentStackSlotIsParameterForward.remove(slot);
            currentStackSlotRecord.remove(slot);
            currentStackSlotString.remove(slot);
            currentStackSlotType.remove(slot);
        }

        JunoType stackSlotType(int slot) {
            return currentStackSlotType.get(slot);
        }

        void recordPush(int slot, Value value) {
            currentStackSlotType.put(slot, value.type());
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
            RecordInstance instance = recordOf.get(value);
            if (instance != null) {
                currentStackSlotRecord.put(slot, instance);
            } else {
                currentStackSlotRecord.remove(slot);
            }
            String string = stringOf.get(value);
            if (string != null) {
                currentStackSlotString.put(slot, string);
            } else {
                currentStackSlotString.remove(slot);
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
            RecordInstance instance = currentStackSlotRecord.get(slot);
            if (instance != null) {
                recordOf.put(value, instance);
            }
            String string = currentStackSlotString.get(slot);
            if (string != null) {
                stringOf.put(value, string);
            }
        }
    }

    /** A record instance that was never actually constructed on any heap — just its component field values. */
    private record RecordInstance(String className, List<Value> fieldValues) {
    }
}
