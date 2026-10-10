package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.FloatBinaryOp;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Primitive operand-stack push/pop/store helpers shared by every {@code lower*}
 * instruction-lowering
 * group: each stack position is a synthetic local slot (see
 * {@link BytecodeToIr}'s class documentation),
 * so "pushing" or "popping" a value means storing to or loading from
 * {@code stackBase + depth}.
 */
final class StackValueOps {
    private StackValueOps() {
    }

    static int pushConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int value,
            ValueTracking tracking) {
        Value target = Value.int32(nextValueId);
        instructions.add(new IrInstruction.Const(target, value));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        tracking.clearStackSlot(stackBase + depth);
        return nextValueId + 1;
    }

    static int pushStringConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
            String value, ValueTracking tracking) {
        Value target = Value.int32(nextValueId);
        instructions.add(new IrInstruction.StringConst(target, value));
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, target));
        tracking.markKnownString(target, value);
        tracking.markStackSlotString(stackBase + depth, value);
        return nextValueId + 1;
    }

    static int pushFloatConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
            float value, ValueTracking tracking) {
        Value target = Value.float32(nextValueId);
        instructions.add(new IrInstruction.FloatConst(target, value));
        storeToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    static int pushDoubleConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
            double value, ValueTracking tracking) {
        Value target = Value.float64(nextValueId);
        instructions.add(new IrInstruction.DoubleConst(target, value));
        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    static int pushLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int local,
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

    static int pushFloatLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
            int local, ValueTracking tracking) {
        Value target = Value.float32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(target, local));
        storeToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    static int pushDoubleLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
            int local, ValueTracking tracking) {
        Value target = Value.float64(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(target, local));
        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId + 1;
    }

    static int pushBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
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

    static int pushFloatBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush,
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

    static int pushDoubleBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush,
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

    static int pushUnary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
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

    static int pushFloatNegate(List<IrInstruction> instructions, int stackBase, int depthBeforePush,
            int nextValueId, ValueTracking tracking) {
        int depth = depthBeforePush - 1;
        Popped operand = popFloat(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = operand.nextValueId();
        Value target = Value.float32(nextValueId++);
        instructions.add(new IrInstruction.FloatNegate(target, operand.value()));
        storeToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId;
    }

    static int pushDoubleNegate(List<IrInstruction> instructions, int stackBase, int depthBeforePush,
            int nextValueId, ValueTracking tracking) {
        int depth = depthBeforePush - 2;
        Popped operand = popDouble(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = operand.nextValueId();
        Value target = Value.float64(nextValueId++);
        instructions.add(new IrInstruction.DoubleNegate(target, operand.value()));
        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        return nextValueId;
    }

    static void storeToStack(List<IrInstruction> instructions, int stackBase, int depth, Value value,
            ValueTracking tracking) {
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, value));
        tracking.recordPush(stackBase + depth, value);
    }

    static Popped pop(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId,
            ValueTracking tracking) {
        Value value = Value.int32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(value, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, value);
        return new Popped(value, nextValueId + 1);
    }

    static Popped popFloat(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId,
            ValueTracking tracking) {
        Value value = Value.float32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(value, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, value);
        return new Popped(value, nextValueId + 1);
    }

    static Popped popDouble(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId,
            ValueTracking tracking) {
        Value value = Value.float64(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(value, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, value);
        tracking.clearStackSlot(stackBase + depthAfterPop + 1);
        return new Popped(value, nextValueId + 1);
    }

    static void storeDoubleToStack(List<IrInstruction> instructions, int stackBase, int depth, Value value,
            ValueTracking tracking) {
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, value));
        tracking.recordPush(stackBase + depth, value);
        tracking.clearStackSlot(stackBase + depth + 1);
    }

    static WidePopped popWide(List<IrInstruction> instructions, int stackBase, int depthAfterPop, int nextValueId,
            ValueTracking tracking) {
        Value low = Value.int32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(low, stackBase + depthAfterPop));
        tracking.recordPop(stackBase + depthAfterPop, low);
        Value high = Value.int32(nextValueId + 1);
        instructions.add(new IrInstruction.LoadLocal(high, stackBase + depthAfterPop + 1));
        tracking.recordPop(stackBase + depthAfterPop + 1, high);
        return new WidePopped(low, high, nextValueId + 2);
    }

    static void storeWideToStack(List<IrInstruction> instructions, int stackBase, int depth, Value low, Value high,
            ValueTracking tracking) {
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth, low));
        tracking.clearStackSlot(stackBase + depth);
        instructions.add(new IrInstruction.StoreLocal(stackBase + depth + 1, high));
        tracking.clearStackSlot(stackBase + depth + 1);
    }

    static int pushWideConst(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, long value,
            ValueTracking tracking) {
        Value low = Value.int32(nextValueId);
        Value high = Value.int32(nextValueId + 1);
        instructions.add(new IrInstruction.LongConst(low, high, value));
        storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        return nextValueId + 2;
    }

    static int pushWideLoad(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId, int local,
            ValueTracking tracking) {
        Value low = Value.int32(nextValueId);
        instructions.add(new IrInstruction.LoadLocal(low, local));
        Value high = Value.int32(nextValueId + 1);
        instructions.add(new IrInstruction.LoadLocal(high, local + 1));
        storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        return nextValueId + 2;
    }

    static int pushLongBinary(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
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

    static int pushLongShift(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
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

    static int pushLongNegate(List<IrInstruction> instructions, int stackBase, int depthBeforePush, int nextValueId,
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
}
