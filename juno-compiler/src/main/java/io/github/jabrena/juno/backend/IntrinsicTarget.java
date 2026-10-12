package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Optional;

/**
 * The instruction-set half of {@link IntrinsicLowering}: the intrinsic table says which runtime function an intrinsic
 * calls with which argument words, and an implementation says how one such call, and the few intrinsics that touch
 * memory directly, look in its assembly. Argument words follow AAPCS layout (a {@code long}/{@code double} is a low
 * and a high word, starting at an even word), which is how the Thumb-2 backend passes them in registers and how the
 * AVR backend lays them out in memory for the shim's generated call thunks.
 */
public interface IntrinsicTarget {
    /** Calls the runtime function {@code function} with {@code words}; the result, if any, waits in the result register(s). */
    void emitShimCall(StringBuilder output, FrameLayout frame, String function, List<WordSource> words);

    /** Stores the one-word result of the last call into {@code target}. */
    void storeResult(StringBuilder output, FrameLayout frame, Value target);

    /** Stores the two-word ({@code long}/{@code double}) result of the last call into {@code target}. */
    void storeWideResult(StringBuilder output, FrameLayout frame, Value target);

    /** Copies one-word {@code source} into {@code target}. */
    void copy(StringBuilder output, FrameLayout frame, Value source, Value target);

    /** Stores the constant {@code value} into {@code target}. */
    void storeImmediate(StringBuilder output, FrameLayout frame, int value, Value target);

    /** Stores one-word {@code value} at byte {@code offset} of the object {@code object} refers to. */
    void storeObjectWord(StringBuilder output, FrameLayout frame, Value object, int offset, Value value);

    /** Loads the word at byte {@code offset} of the object {@code object} refers to, into {@code target} if present. */
    void loadObjectWord(StringBuilder output, FrameLayout frame, Value object, int offset, Optional<Value> target);

    /** The diagnostic for an intrinsic this backend cannot lower. */
    CompileException unsupported(String detail);
}
