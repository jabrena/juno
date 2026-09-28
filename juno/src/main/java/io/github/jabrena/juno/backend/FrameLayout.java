package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;

import java.util.List;

/** Every value/local's fixed stack offset, and the total (8-byte-aligned) frame size to reserve. */
record FrameLayout(int frameSize, int[] valueOffsets, int[] localOffsets) {
    int valueOffset(Value value) {
        return valueOffsets[value.id()];
    }

    int localOffset(int local) {
        return localOffsets[local];
    }

    static FrameLayout of(IrMethod method) {
        List<Value> values = method.values();
        int numValues = values.size();
        int numLocals = method.maxLocals();
        int[] valueOffsets = new int[numValues];
        int valueBytes = 0;
        for (int i = 0; i < numValues; i++) {
            valueOffsets[i] = valueBytes;
            valueBytes += valueWidth(values.get(i).type());
        }
        int[] localOffsets = new int[numLocals];
        // Every local gets a full 8-byte slot regardless of its actual type: a JVM long/double local
        // only ever uses ONE local index (the low-numbered half of the two JVMS reserves), but reusing this backend's
        // otherwise-compact 4-byte-per-index model just for that one index would let its 8 bytes spill
        // into local index N+1's own storage. Uniform 8-byte slots make that overlap impossible by
        // construction, at the cost of wasting 4 bytes per plain int local.
        for (int i = 0; i < numLocals; i++) {
            localOffsets[i] = valueBytes + i * 8;
        }
        int rawSize = valueBytes + numLocals * 8;
        // PUSH_BYTES (36) isn't itself a multiple of 8; round the *total* prologue adjustment up to
        // the next multiple of 8 so `sp` stays AAPCS-aligned for every `bl`, then subtract PUSH_BYTES
        // back out to get the frame size to actually `sub sp` by.
        int frameSize = AsmEmitter.roundUp(AsmEmitter.PUSH_BYTES + rawSize, 8) - AsmEmitter.PUSH_BYTES;
        return new FrameLayout(frameSize, valueOffsets, localOffsets);
    }

    /**
     * {@code long}/{@code double} boundary {@link Value}s ({@link JunoType#INT64}/{@link JunoType#FLOAT64})
     * need 8 bytes; everything else (including {@link JunoType#FLOAT32}, a raw bit pattern) fits in 4.
     */
    private static int valueWidth(JunoType type) {
        return isWide(type) ? 8 : AsmEmitter.WORD;
    }

    static boolean isWide(JunoType type) {
        return type == JunoType.INT64 || type == JunoType.FLOAT64;
    }
}
