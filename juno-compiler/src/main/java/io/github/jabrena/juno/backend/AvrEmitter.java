package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The instruction-level building blocks of the AVR backend, the counterpart of {@link AsmEmitter}. A Java word is four
 * bytes in four consecutive registers, least significant first; this class names such a quad by its lowest register
 * ({@code 22} is r22-r25, the avr-gcc 32-bit return register; {@code 18} is r18-r21). Every value and local lives in
 * a stack slot addressed from the frame pointer Y (r29:r28) — {@code ldd}/{@code std} reach Y+63, slots further away
 * go through X — and Z (r31:r30) is kept free for object and array pointers. r1 is avr-gcc's zero register and is
 * never written; r0 is scratch.
 *
 * <p>A reference is a 16-bit data address held in a 32-bit word, sign-extended from bit 15 exactly as the shim's
 * {@code intptr_t} casts do, so a reference compares equal however it was produced.
 *
 * <p>Calls into the runtime shim pass their argument words through the global {@code juno_avr_args} array instead of
 * registers (see {@link AvrShimThunks}): the generated code stores AAPCS-ordered words there and calls
 * {@code juno_avr_thunk_<function>}, a C++ thunk that reads them back with the function's real parameter types.
 */
final class AvrEmitter {
    static final int WORD = 4;
    /** The global argument area every generated function and shim thunk reads its incoming words from. */
    static final String ARGUMENTS = "juno_avr_args";
    /** avr-gcc's 32-bit argument/return register quad. */
    static final int A = 22;
    /** The second 32-bit argument quad, and the low word of a 64-bit return value. */
    static final int B = 18;
    /** {@code ldd}/{@code std} displacements stop here. */
    private static final int MAX_DISPLACEMENT = 63;

    private final Map<String, String> stringLiteralSymbols;
    private final Set<String> thunkFunctions = new TreeSet<>();
    private int labelCounter;
    private int argumentWords;

    AvrEmitter(Map<String, String> stringLiteralSymbols) {
        this.stringLiteralSymbols = stringLiteralSymbols;
    }

    /** Every runtime function called through a thunk, in name order. */
    Set<String> thunkFunctions() {
        return thunkFunctions;
    }

    /** The most argument words any call stores in {@link #ARGUMENTS}. */
    int argumentWords() {
        return argumentWords;
    }

    String newLabel(String prefix) {
        return prefix + (labelCounter++);
    }

    static String r(int register) {
        return "r" + register;
    }

    /** {@code ldi} only reaches r16-r31, which every quad this backend loads immediates into is. */
    void loadImmediate(StringBuilder output, int quad, int value) {
        for (int index = 0; index < WORD; index++) {
            output.append("    ldi ").append(r(quad + index)).append(", ").append((value >>> (8 * index)) & 0xFF)
                    .append('\n');
        }
    }

    /** The address of a data symbol, sign-extended from bit 15 like every other reference. */
    void loadAddress(StringBuilder output, int quad, String symbol) {
        output.append("    ldi ").append(r(quad)).append(", lo8(").append(symbol).append(")\n")
                .append("    ldi ").append(r(quad + 1)).append(", hi8(").append(symbol).append(")\n");
        signExtend16(output, quad);
    }

    /** Fills the upper two bytes of {@code quad} with bit 15. */
    static void signExtend16(StringBuilder output, int quad) {
        output.append("    mov ").append(r(quad + 2)).append(", ").append(r(quad + 1)).append('\n')
                .append("    lsl ").append(r(quad + 2)).append('\n')
                .append("    sbc ").append(r(quad + 2)).append(", ").append(r(quad + 2)).append('\n')
                .append("    mov ").append(r(quad + 3)).append(", ").append(r(quad + 2)).append('\n');
    }

    void loadStringAddress(StringBuilder output, int quad, String literal) {
        loadAddress(output, quad, java.util.Objects.requireNonNull(stringLiteralSymbols.get(literal)));
    }

    void load(StringBuilder output, FrameLayout frame, int quad, Value value) {
        Integer constant = frame.constant(value);
        if (constant != null) {
            loadImmediate(output, quad, constant);
        } else {
            loadSlot(output, quad, frame.valueOffset(value), WORD);
        }
    }

    void store(StringBuilder output, FrameLayout frame, int quad, Value value) {
        storeSlot(output, quad, frame.valueOffset(value), WORD);
    }

    /** A {@code long}/{@code double} value: its low word into {@code lowQuad}, its high word into {@code highQuad}. */
    void load64(StringBuilder output, FrameLayout frame, int lowQuad, int highQuad, Value value) {
        loadSlot(output, lowQuad, frame.valueOffset(value), WORD);
        loadSlot(output, highQuad, frame.valueOffset(value) + WORD, WORD);
    }

    void store64(StringBuilder output, FrameLayout frame, int lowQuad, int highQuad, Value value) {
        storeSlot(output, lowQuad, frame.valueOffset(value), WORD);
        storeSlot(output, highQuad, frame.valueOffset(value) + WORD, WORD);
    }

    /** The low 16 bits of a reference value into Z. */
    void loadPointer(StringBuilder output, FrameLayout frame, Value reference) {
        Integer constant = frame.constant(reference);
        if (constant != null) {
            output.append("    ldi r30, ").append(constant & 0xFF).append('\n')
                    .append("    ldi r31, ").append((constant >>> 8) & 0xFF).append('\n');
        } else {
            loadSlot(output, 30, frame.valueOffset(reference), 2);
        }
    }

    /** {@code bytes} bytes of the slot at frame offset {@code offset} into {@code first}, {@code first + 1}, ... */
    void loadSlot(StringBuilder output, int first, int offset, int bytes) {
        int base = offset + 1;
        if (base + bytes - 1 <= MAX_DISPLACEMENT) {
            for (int index = 0; index < bytes; index++) {
                output.append("    ldd ").append(r(first + index)).append(", Y+").append(base + index).append('\n');
            }
            return;
        }
        pointX(output, base);
        for (int index = 0; index < bytes; index++) {
            output.append("    ld ").append(r(first + index)).append(index + 1 < bytes ? ", X+\n" : ", X\n");
        }
    }

    void storeSlot(StringBuilder output, int first, int offset, int bytes) {
        int base = offset + 1;
        if (base + bytes - 1 <= MAX_DISPLACEMENT) {
            for (int index = 0; index < bytes; index++) {
                output.append("    std Y+").append(base + index).append(", ").append(r(first + index)).append('\n');
            }
            return;
        }
        pointX(output, base);
        for (int index = 0; index < bytes; index++) {
            output.append("    st X").append(index + 1 < bytes ? "+" : "").append(", ").append(r(first + index))
                    .append('\n');
        }
    }

    /** X = Y + {@code base}: the frame starts one byte above the stack pointer the prologue copied into Y. */
    private static void pointX(StringBuilder output, int base) {
        output.append("    movw r26, r28\n")
                .append("    subi r26, lo8(-(").append(base).append("))\n")
                .append("    sbci r27, hi8(-(").append(base).append("))\n");
    }

    /** {@code bytes} bytes at Z+{@code offset} into {@code first}, ...; moves Z itself when the offset is out of reach. */
    static void loadFromZ(StringBuilder output, int first, int offset, int bytes) {
        int base = reachZ(output, offset, bytes);
        for (int index = 0; index < bytes; index++) {
            output.append("    ldd ").append(r(first + index)).append(", Z+").append(base + index).append('\n');
        }
    }

    static void storeToZ(StringBuilder output, int first, int offset, int bytes) {
        int base = reachZ(output, offset, bytes);
        for (int index = 0; index < bytes; index++) {
            output.append("    std Z+").append(base + index).append(", ").append(r(first + index)).append('\n');
        }
    }

    private static int reachZ(StringBuilder output, int offset, int bytes) {
        if (offset + bytes - 1 <= MAX_DISPLACEMENT) {
            return offset;
        }
        output.append("    subi r30, lo8(-(").append(offset).append("))\n")
                .append("    sbci r31, hi8(-(").append(offset).append("))\n");
        return 0;
    }

    void loadWord(StringBuilder output, FrameLayout frame, WordSource source, int quad) {
        switch (source) {
            case WordSource.FromValue from -> load(output, frame, quad, from.value());
            case WordSource.FromValueLow from -> loadSlot(output, quad, frame.valueOffset(from.value()), WORD);
            case WordSource.FromValueHigh from ->
                    loadSlot(output, quad, frame.valueOffset(from.value()) + WORD, WORD);
            case WordSource.StringAddress address -> loadStringAddress(output, quad, address.literal());
            case WordSource.Immediate immediate -> loadImmediate(output, quad, immediate.value());
        }
    }

    /** Stores quad {@code quad} as argument word {@code index}. */
    void storeArgument(StringBuilder output, int quad, int index) {
        argumentWords = Math.max(argumentWords, index + 1);
        for (int offset = 0; offset < WORD; offset++) {
            output.append("    sts ").append(ARGUMENTS).append('+').append(index * WORD + offset).append(", ")
                    .append(r(quad + offset)).append('\n');
        }
    }

    /** Loads argument word {@code index} into {@code quad}. */
    void loadArgument(StringBuilder output, int quad, int index) {
        argumentWords = Math.max(argumentWords, index + 1);
        for (int offset = 0; offset < WORD; offset++) {
            output.append("    lds ").append(r(quad + offset)).append(", ").append(ARGUMENTS).append('+')
                    .append(index * WORD + offset).append('\n');
        }
    }

    void storeArguments(StringBuilder output, FrameLayout frame, List<WordSource> words) {
        for (int index = 0; index < words.size(); index++) {
            loadWord(output, frame, words.get(index), A);
            storeArgument(output, A, index);
        }
    }

    /**
     * Calls runtime function {@code function} through its thunk; a one-word result comes back in r22-r25, a
     * two-word one in r18-r25 (low word first).
     */
    void emitThunkCall(StringBuilder output, FrameLayout frame, String function, List<WordSource> words) {
        thunkFunctions.add(function);
        storeArguments(output, frame, words);
        output.append("    call ").append(AvrShimThunks.thunkName(function)).append('\n');
    }

    /** Sets the flags as {@code cp} of quad {@code left} against quad {@code right}, all four bytes. */
    static void compareQuads(StringBuilder output, int left, int right) {
        output.append("    cp ").append(r(left)).append(", ").append(r(right)).append('\n');
        for (int index = 1; index < WORD; index++) {
            output.append("    cpc ").append(r(left + index)).append(", ").append(r(right + index)).append('\n');
        }
    }

    /** Sets Z (the zero flag) when quad {@code quad} is zero. */
    static void testZero(StringBuilder output, int quad) {
        output.append("    cp ").append(r(quad)).append(", r1\n");
        for (int index = 1; index < WORD; index++) {
            output.append("    cpc ").append(r(quad + index)).append(", r1\n");
        }
    }
}
