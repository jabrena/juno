package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * One method's JVM operand stack, kept in synthetic local slots: the stack position {@code depth} lives in local
 * {@code base + depth}, where {@code base} is the method's {@code max_locals}. Pushing a value stores it to the top
 * slot and popping loads it back into a fresh {@link Value}, so a value pushed by one predecessor block and consumed
 * in a common successor flows through the slot (see {@link BytecodeToIr}).
 *
 * <p>A {@code long} occupies two slots, low half first; a {@code double} occupies two slots as one 64-bit value in
 * the lower one. Every load and store keeps {@link ValueTracking} in step with the slots.
 */
final class OperandStack {
    private final int base;
    private final FreshValues values;
    private final IrBlockWriter code;
    private final ValueTracking facts;
    private int depth;

    OperandStack(int base, FreshValues values, IrBlockWriter code, ValueTracking facts) {
        this.base = base;
        this.values = values;
        this.code = code;
        this.facts = facts;
    }

    /** Starts lowering a block whose stack holds {@code entryDepth} slots on entry. */
    void startBlock(int entryDepth) {
        depth = entryDepth;
        facts.startBlock();
    }

    /** The slot of the stack's bottom position, where an exception handler finds the caught exception. */
    int bottomSlot() {
        return base;
    }

    void pushInt(int constant) {
        Value value = values.int32();
        code.emit(new IrInstruction.Const(value, constant));
        facts.markKnownInt(value, constant);
        push(value);
    }

    void pushLong(long constant) {
        Value low = values.int32();
        Value high = values.int32();
        code.emit(new IrInstruction.LongConst(low, high, constant));
        facts.markKnownInt(low, (int) constant);
        facts.markKnownInt(high, (int) (constant >> 32));
        pushHalves(low, high);
    }

    void pushFloat(float constant) {
        Value value = values.float32();
        code.emit(new IrInstruction.FloatConst(value, constant));
        push(value);
    }

    void pushDouble(double constant) {
        Value value = values.float64();
        code.emit(new IrInstruction.DoubleConst(value, constant));
        push(value);
    }

    /** Pushes a string literal, remembering its text for intrinsics that need it at compile time. */
    void pushString(String literal) {
        Value value = values.int32();
        code.emit(new IrInstruction.StringConst(value, literal));
        facts.markKnownString(value, literal);
        push(value);
    }

    /** Pushes {@code value}, taking two slots for a {@code long} (split into halves) or a {@code double}. */
    void push(Value value) {
        switch (value.type()) {
            case INT64 -> {
                Value low = values.int32();
                Value high = values.int32();
                code.emit(new IrInstruction.UnpackLong(low, high, value));
                pushHalves(low, high);
            }
            case FLOAT64 -> {
                store(slot(depth), value);
                facts.clearSlot(slot(depth) + 1);
                depth += 2;
            }
            case INT32, FLOAT32 -> {
                store(slot(depth), value);
                depth++;
            }
        }
    }

    /** Pushes a {@code long} as its two 32-bit halves. */
    void pushHalves(Value low, Value high) {
        code.emit(new IrInstruction.StoreLocal(slot(depth), low));
        facts.recordPush(slot(depth), low);
        code.emit(new IrInstruction.StoreLocal(slot(depth) + 1, high));
        facts.recordPush(slot(depth) + 1, high);
        depth += 2;
    }

    /** Overwrites the top value in place, e.g. a {@code new} placeholder once its constructor built the real handle. */
    void replaceTop(Value value) {
        store(slot(depth - 1), value);
    }

    /** Stores {@code value} as the stack's only value, where a handler expects the exception it catches. */
    void storeAsOnlyValue(Value value) {
        store(base, value);
    }

    Value pop() {
        return popAs(values.int32());
    }

    Value popFloat() {
        return popAs(values.float32());
    }

    Value popDouble() {
        depth -= 2;
        Value value = values.float64();
        code.emit(new IrInstruction.LoadLocal(value, slot(depth)));
        facts.recordPop(slot(depth), value);
        facts.clearSlot(slot(depth) + 1);
        return value;
    }

    /** Pops a {@code long} as its two 32-bit halves. */
    LongHalves popHalves() {
        depth -= 2;
        Value low = values.int32();
        code.emit(new IrInstruction.LoadLocal(low, slot(depth)));
        facts.recordPop(slot(depth), low);
        Value high = values.int32();
        code.emit(new IrInstruction.LoadLocal(high, slot(depth) + 1));
        facts.recordPop(slot(depth) + 1, high);
        return new LongHalves(low, high);
    }

    /** Pops a {@code long} as one packed 64-bit value. */
    Value popLong() {
        LongHalves halves = popHalves();
        Value packed = values.int64();
        code.emit(new IrInstruction.PackLong(packed, halves.low(), halves.high()));
        return packed;
    }

    /** Pops a value of {@code type}: a {@code long} comes back packed. */
    Value pop(JunoType type) {
        return switch (type) {
            case INT32 -> pop();
            case INT64 -> popLong();
            case FLOAT32 -> popFloat();
            case FLOAT64 -> popDouble();
        };
    }

    /** Discards the top {@code slots} slots without reading them ({@code pop}/{@code pop2}). */
    void drop(int slots) {
        depth -= slots;
    }

    /** Duplicates the top one-slot value ({@code dup}). */
    void dup() {
        JunoType type = facts.slotType(slot(depth - 1));
        Value top = values.of(type == null ? JunoType.INT32 : type);
        code.emit(new IrInstruction.LoadLocal(top, slot(depth - 1)));
        facts.recordPop(slot(depth - 1), top);
        store(slot(depth), top);
        depth++;
    }

    /**
     * The rest of the JVM's dup family, on slots as the JVM counts them: copies the top {@code copied} slots and
     * inserts the copy below the {@code skipped} slots under them. {@code dup_x1} is (1, 1), {@code dup_x2} (1, 2),
     * {@code dup2} (2, 0), {@code dup2_x1} (2, 1) and {@code dup2_x2} (2, 2). A {@code double} moves as the one
     * 64-bit value it is; a {@code long}'s halves move as two slots. Every slot is loaded before any is stored, so the
     * shuffle may overwrite the slots it reads.
     */
    void duplicate(int copied, int skipped) {
        int window = copied + skipped;
        List<Value> items = new ArrayList<>();
        int split = -1;
        for (int position = depth - window; position < depth; ) {
            if (position == depth - copied) {
                split = items.size();
            }
            JunoType type = facts.slotType(slot(position));
            Value value = values.of(type == null ? JunoType.INT32 : type);
            code.emit(new IrInstruction.LoadLocal(value, slot(position)));
            facts.recordPop(slot(position), value);
            items.add(value);
            position += type == JunoType.FLOAT64 ? 2 : 1;
        }
        if (split < 0) {
            throw new IllegalStateException("a dup splits a two-slot value: copied " + copied + ", skipped " + skipped);
        }
        depth -= window;
        List<Value> copies = items.subList(split, items.size());
        copies.forEach(this::push);
        items.subList(0, split).forEach(this::push);
        copies.forEach(this::push);
    }

    /** Reads the int in the slot {@code slotsFromTop} below the top (1 is the top itself) without popping it. */
    Value peekInt(int slotsFromTop) {
        Value value = values.int32();
        code.emit(new IrInstruction.LoadLocal(value, slot(depth - slotsFromTop)));
        return value;
    }

    /**
     * Pops the top value when it is an int literal pushed by the two instructions just emitted, un-emitting that push;
     * empty otherwise. Array sizes need this: there is no heap, so every array is a fixed-size C array.
     */
    OptionalInt popLiteralInt() {
        int slot = slot(depth - 1);
        List<IrInstruction> pushed = code.last(2);
        if (pushed.size() == 2
                && pushed.get(0) instanceof IrInstruction.Const constant
                && pushed.get(1) instanceof IrInstruction.StoreLocal store
                && store.local() == slot && constant.target().equals(store.value())) {
            code.removeLast(2);
            facts.clearSlot(slot);
            depth--;
            return OptionalInt.of(constant.value());
        }
        return OptionalInt.empty();
    }

    private Value popAs(Value value) {
        depth--;
        code.emit(new IrInstruction.LoadLocal(value, slot(depth)));
        facts.recordPop(slot(depth), value);
        return value;
    }

    private void store(int slot, Value value) {
        code.emit(new IrInstruction.StoreLocal(slot, value));
        facts.recordPush(slot, value);
    }

    private int slot(int position) {
        return base + position;
    }

    /** A {@code long} as the two 32-bit halves it occupies on the stack and in locals. */
    record LongHalves(Value low, Value high) {
    }
}
