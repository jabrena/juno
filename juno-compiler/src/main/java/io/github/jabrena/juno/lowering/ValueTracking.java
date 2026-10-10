package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-method dataflow tracking used while lowering: which values are known-length arrays, forwarded
 * parameters, record instances, or string literals, and which stack slot currently holds which of
 * those (reset at the start of each block, since stack slots are reused as depth rises and falls).
 */
final class ValueTracking {
        private boolean divisionByZeroUnwinds;
        private final Map<Value, Integer> arrayLength = new HashMap<>();
        private final Set<Value> parameterForwarded = new HashSet<>();
        private final Map<Value, RecordInstance> recordOf = new HashMap<>();
        private final Map<Value, String> stringOf = new HashMap<>();
        private Map<Integer, Integer> currentStackSlotLength = new HashMap<>();
        private Set<Integer> currentStackSlotIsParameterForward = new HashSet<>();
        private Map<Integer, RecordInstance> currentStackSlotRecord = new HashMap<>();
        private Map<Integer, String> currentStackSlotString = new HashMap<>();
        private Map<Integer, JunoType> currentStackSlotType = new HashMap<>();

        /** Whether an integer division by zero with no local handler raises an exception for callers to catch. */
        void unwindDivisionByZero(boolean unwinds) {
            divisionByZeroUnwinds = unwinds;
        }

        boolean divisionByZeroUnwinds() {
            return divisionByZeroUnwinds;
        }

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

        @Nullable Integer knownLength(Value value) {
            return arrayLength.get(value);
        }

        boolean isParameterForward(Value value) {
            return parameterForwarded.contains(value);
        }

        void markKnownRecord(Value value, RecordInstance instance) {
            recordOf.put(value, instance);
        }

        @Nullable RecordInstance knownRecord(Value value) {
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

        @Nullable String knownString(Value value) {
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

        @Nullable JunoType stackSlotType(int slot) {
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
