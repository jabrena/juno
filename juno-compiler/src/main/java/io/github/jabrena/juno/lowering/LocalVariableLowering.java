package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import static io.github.jabrena.juno.bytecode.Opcodes.*;

/** Local-variable loads and stores ({@code *load*}, {@code *store*}) and {@code iinc}. */
final class LocalVariableLowering implements OpcodeGroup {
    private final MethodContext context;

    LocalVariableLowering(MethodContext context) {
        this.context = context;
    }

    @Override
    public void registerOn(OpcodeTable table) {
        FreshValues values = context.values();
        OperandStack stack = context.stack();
        table.on(ILOAD, instruction -> loadReference(instruction.operandA()));
        table.on(ALOAD, instruction -> loadReference(instruction.operandA()));
        table.on(LLOAD, instruction -> loadLong(instruction.operandA()));
        table.on(FLOAD, instruction -> load(values.float32(), instruction.operandA()));
        table.on(DLOAD, instruction -> load(values.float64(), instruction.operandA()));
        table.on(ILOAD_0, ILOAD_3, instruction -> loadReference(instruction.opcode() - ILOAD_0));
        table.on(LLOAD_0, LLOAD_3, instruction -> loadLong(instruction.opcode() - LLOAD_0));
        table.on(FLOAD_0, FLOAD_3, instruction -> load(values.float32(), instruction.opcode() - FLOAD_0));
        table.on(DLOAD_0, DLOAD_3, instruction -> load(values.float64(), instruction.opcode() - DLOAD_0));
        table.on(ALOAD_0, ALOAD_3, instruction -> loadReference(instruction.opcode() - ALOAD_0));
        table.on(ISTORE, instruction -> storeReference(instruction.operandA()));
        table.on(ASTORE, instruction -> storeReference(instruction.operandA()));
        table.on(LSTORE, instruction -> storeLong(instruction.operandA()));
        table.on(FSTORE, instruction -> store(instruction.operandA(), stack.popFloat()));
        table.on(DSTORE, instruction -> store(instruction.operandA(), stack.popDouble()));
        table.on(ISTORE_0, ISTORE_3, instruction -> storeReference(instruction.opcode() - ISTORE_0));
        table.on(LSTORE_0, LSTORE_3, instruction -> storeLong(instruction.opcode() - LSTORE_0));
        table.on(FSTORE_0, FSTORE_3, instruction -> store(instruction.opcode() - FSTORE_0, stack.popFloat()));
        table.on(DSTORE_0, DSTORE_3, instruction -> store(instruction.opcode() - DSTORE_0, stack.popDouble()));
        table.on(ASTORE_0, ASTORE_3, instruction -> storeReference(instruction.opcode() - ASTORE_0));
        table.on(IINC, instruction -> increment(instruction.operandA(), instruction.operandB()));
    }

    /** Loads an int or reference, carrying what the local is known to hold (an array's length, a literal). */
    private void loadReference(int local) {
        Value value = context.values().int32();
        context.emit(new IrInstruction.LoadLocal(value, local));
        Integer arrayLength = context.locals().knownArrayLength(local);
        if (arrayLength != null) {
            context.facts().markKnownArray(value, arrayLength);
        }
        String literal = context.locals().knownString(local);
        if (literal != null) {
            context.facts().markKnownString(value, literal);
        }
        context.stack().push(value);
    }

    private void load(Value value, int local) {
        context.emit(new IrInstruction.LoadLocal(value, local));
        context.stack().push(value);
    }

    /** A {@code long} local occupies {@code local} (low half) and {@code local + 1} (high half). */
    private void loadLong(int local) {
        Value low = context.values().int32();
        context.emit(new IrInstruction.LoadLocal(low, local));
        Value high = context.values().int32();
        context.emit(new IrInstruction.LoadLocal(high, local + 1));
        context.stack().pushHalves(low, high);
    }

    private void storeReference(int local) {
        Value value = context.stack().pop();
        store(local, value);
        context.locals().rememberStore(local, value, context.facts());
    }

    private void storeLong(int local) {
        OperandStack.LongHalves value = context.stack().popHalves();
        context.emit(new IrInstruction.StoreLocal(local, value.low()));
        context.emit(new IrInstruction.StoreLocal(local + 1, value.high()));
    }

    private void store(int local, Value value) {
        context.emit(new IrInstruction.StoreLocal(local, value));
    }

    private void increment(int local, int amount) {
        Value loaded = context.values().int32();
        context.emit(new IrInstruction.LoadLocal(loaded, local));
        Value constant = context.values().int32();
        context.emit(new IrInstruction.Const(constant, amount));
        Value sum = context.values().int32();
        context.emit(new IrInstruction.Binary(sum, BinaryOp.ADD, loaded, constant));
        context.emit(new IrInstruction.StoreLocal(local, sum));
    }
}
