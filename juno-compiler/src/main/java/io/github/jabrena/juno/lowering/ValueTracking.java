package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-method facts about values that a real type system would normally carry: whether a value is a known-length
 * local array (safe to bounds-check) or a compile-time string literal. Facts about a {@link Value} are never reset,
 * since values are single-assignment. The stack-slot views record which slot currently holds which of those, and are
 * reset at the start of every block: stack slots are reused as depth rises and falls, and two branches could leave
 * different values in the same slot before a merge.
 */
final class ValueTracking {
    private final Map<Value, Integer> arrayLength = new HashMap<>();
    private final Map<Value, String> stringOf = new HashMap<>();
    private final Map<Value, Integer> intOf = new HashMap<>();
    private Map<Integer, Integer> slotLength = new HashMap<>();
    private Map<Integer, String> slotString = new HashMap<>();
    private Map<Integer, Integer> slotInt = new HashMap<>();
    private Map<Integer, JunoType> slotType = new HashMap<>();

    void startBlock() {
        slotLength = new HashMap<>();
        slotString = new HashMap<>();
        slotInt = new HashMap<>();
        slotType = new HashMap<>();
    }

    void markKnownArray(Value value, int length) {
        arrayLength.put(value, length);
    }

    @Nullable Integer knownLength(Value value) {
        return arrayLength.get(value);
    }

    void markKnownString(Value value, String literal) {
        stringOf.put(value, literal);
    }

    @Nullable String knownString(Value value) {
        return stringOf.get(value);
    }

    /** Records that the value is the int constant {@code constant}, as an {@code iconst}/{@code ldc} push makes it. */
    void markKnownInt(Value value, int constant) {
        intOf.put(value, constant);
    }

    @Nullable Integer knownInt(Value value) {
        return intOf.get(value);
    }

    /** Tags the value occupying {@code slot} as a compile-time string literal, without a {@link Value} in hand. */
    void markSlotString(int slot, String literal) {
        slotString.put(slot, literal);
    }

    void clearSlot(int slot) {
        slotLength.remove(slot);
        slotString.remove(slot);
        slotInt.remove(slot);
        slotType.remove(slot);
    }

    @Nullable JunoType slotType(int slot) {
        return slotType.get(slot);
    }

    /** {@code value} was stored to {@code slot}: the slot now carries the value's facts. */
    void recordPush(int slot, Value value) {
        slotType.put(slot, value.type());
        putOrRemove(slotInt, slot, intOf.get(value));
        putOrRemove(slotLength, slot, arrayLength.get(value));
        putOrRemove(slotString, slot, stringOf.get(value));
    }

    /** {@code value} was loaded from {@code slot}: the value inherits the slot's facts. */
    void recordPop(int slot, Value value) {
        Integer length = slotLength.get(slot);
        if (length != null) {
            arrayLength.put(value, length);
        }
        String string = slotString.get(slot);
        if (string != null) {
            stringOf.put(value, string);
        }
        Integer constant = slotInt.get(slot);
        if (constant != null) {
            intOf.put(value, constant);
        }
    }

    private <T> void putOrRemove(Map<Integer, T> slots, int slot, @Nullable T fact) {
        if (fact == null) {
            slots.remove(slot);
        } else {
            slots.put(slot, fact);
        }
    }
}
