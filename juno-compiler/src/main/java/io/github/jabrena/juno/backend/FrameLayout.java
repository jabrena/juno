package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.Value;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Every value/local's fixed stack offset, and the total (8-byte-aligned) frame size to reserve. A value defined by
 * exactly one {@link IrInstruction.Const} is rematerialized instead: it has no slot, and every use loads its
 * immediate (or folds it into the instruction), so the constant is never stored and reloaded.
 */
@SuppressWarnings("ArrayRecordComponent")
public record FrameLayout(int frameSize, int[] valueOffsets, int[] localOffsets, Map<Integer, Integer> constants) {
    /** Bytes of one Java word on every target: an {@code int}, a reference, half a {@code long}/{@code double}. */
    public static final int WORD = 4;
    /** Bytes the Thumb-2 prologue's {@code push {r4-r11, lr}} reserves above the frame {@link #of} lays out. */
    public static final int PUSH_BYTES = 9 * WORD;

    public FrameLayout {
        constants = Map.copyOf(constants);
    }

    public int valueOffset(Value value) {
        if (constants.containsKey(value.id())) {
            throw new IllegalStateException("constant " + value + " is rematerialized and has no stack slot");
        }
        return valueOffsets[value.id()];
    }

    /** The value's constant, when it is a rematerialized {@code Const}; otherwise null. */
    @Nullable
    public Integer constant(Value value) {
        return constants.get(value.id());
    }

    public int localOffset(int local) {
        if (localOffsets[local] < 0) {
            throw new IllegalStateException("local slot " + local + " is never loaded or stored and has no stack word");
        }
        return localOffsets[local];
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FrameLayout that)) {
            return false;
        }
        return frameSize == that.frameSize
                && Arrays.equals(valueOffsets, that.valueOffsets)
                && Arrays.equals(localOffsets, that.localOffsets)
                && constants.equals(that.constants);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(frameSize, constants);
        result = 31 * result + Arrays.hashCode(valueOffsets);
        return 31 * result + Arrays.hashCode(localOffsets);
    }

    @Override
    public String toString() {
        return "FrameLayout[frameSize=" + frameSize + ", valueOffsets=" + Arrays.toString(valueOffsets)
                + ", localOffsets=" + Arrays.toString(localOffsets) + ", constants=" + constants + "]";
    }

    public static FrameLayout of(IrMethod method) {
        return new FrameAllocator(method, PUSH_BYTES, 8).layout();
    }

    /**
     * The same slots with nothing reserved for alignment: for a target whose stack has no alignment rule (AVR),
     * {@link #frameSize()} is exactly the bytes the values and locals occupy.
     */
    public static FrameLayout packed(IrMethod method) {
        return new FrameAllocator(method, 0, 1).layout();
    }
}
