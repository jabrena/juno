package io.github.jabrena.juno.backend.xtensa;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.WordSource;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stack-slot moves, immediates and calls in GNU Xtensa assembly, for the windowed-register ABI the ESP32 core is
 * built with. Inside a function {@code a1} is the stack pointer, {@code a2}-{@code a7} hold the incoming argument
 * words until the prologue copies them into their slots, and a call through {@code call8} passes its argument words
 * in {@code a10}-{@code a15} (seen by the callee as {@code a2}-{@code a7}) and gets its result back in {@code a10}
 * ({@code a10}/{@code a11} for two words).
 *
 * <p>The core assembles with {@code -mlongcalls}, so the assembler expands every {@code call8} into an
 * {@code l32r a8} of the target's address plus {@code callx8 a8}, and every {@code movi} whose operand is a symbol or
 * does not fit 12 bits into an {@code l32r} from a literal it places itself. {@link #A} is therefore scratch only
 * within one IR instruction, never across a call; that is all the storage model needs, as every value lives in its
 * stack slot.
 */
final class XtensaEmitter {
    /** The primary scratch register; every call clobbers it. */
    static final String A = "a8";
    /** The secondary scratch register. */
    static final String B = "a9";
    /** A third scratch register, free outside the argument staging of a call. */
    static final String C = "a10";
    /** Bytes of one Java word. */
    static final int WORD = FrameLayout.WORD;
    /** Argument words a {@code call8} passes in registers; the rest go on the stack, below the caller's slots. */
    static final int REGISTER_ARGUMENT_WORDS = 6;

    /** Largest offset {@code l32i}/{@code s32i} encode (an unsigned 8-bit count of words). */
    private static final int MAX_WORD_OFFSET = 1020;
    /** Address scratch for out-of-range slot offsets: an incoming argument register, free once they are spilled. */
    private static final String ADDRESS = "a7";

    private final Map<String, String> stringLiteralSymbols;
    private int labelCounter;
    /** Bytes at the bottom of the current frame, below its slots, that hold outgoing stack arguments. */
    private int slotBase;
    /** The most outgoing stack-argument bytes any call in the current method has needed. */
    private int stackArgumentBytes;

    XtensaEmitter(Map<String, String> stringLiteralSymbols) {
        this.stringLiteralSymbols = stringLiteralSymbols;
    }

    String newLabel(String prefix) {
        return prefix + labelCounter++;
    }

    /** The label counter, for {@link #rewindLabels} to restore when a method is emitted a second time. */
    int labelMark() {
        return labelCounter;
    }

    void rewindLabels(int mark) {
        labelCounter = mark;
    }

    /**
     * Starts a method whose slots sit {@code outgoingBytes} above {@code a1}, leaving the bottom of the frame to the
     * stack arguments of its calls (the windowed ABI passes argument word {@code i >= 6} at the caller's
     * {@code a1 + 4 * (i - 6)}).
     */
    void beginFrame(int outgoingBytes) {
        slotBase = outgoingBytes;
        stackArgumentBytes = 0;
    }

    /** The outgoing stack-argument bytes the current method's calls have needed so far. */
    int stackArgumentBytes() {
        return stackArgumentBytes;
    }

    /** {@code register = value}; the assembler turns a value that does not fit {@code movi} into a literal load. */
    void loadImmediate(StringBuilder output, String register, int value) {
        output.append("    movi ").append(register).append(", ").append(value).append('\n');
    }

    /** {@code register = &symbol}, through a literal the assembler places. */
    void loadAddress(StringBuilder output, String register, String symbol) {
        output.append("    movi ").append(register).append(", ").append(symbol).append('\n');
    }

    void loadStringAddress(StringBuilder output, String register, String literal) {
        loadAddress(output, register, Objects.requireNonNull(stringLiteralSymbols.get(literal)));
    }

    void load(StringBuilder output, FrameLayout frame, String register, Value value) {
        Integer constant = frame.constant(value);
        if (constant != null) {
            loadImmediate(output, register, constant);
        } else {
            loadSlot(output, register, frame.valueOffset(value));
        }
    }

    void store(StringBuilder output, FrameLayout frame, String register, Value value) {
        storeSlot(output, register, frame.valueOffset(value));
    }

    /** Loads a {@code long}/{@code double} value's low word into {@code low} and its high word into {@code high}. */
    void load64(StringBuilder output, FrameLayout frame, String low, String high, Value value) {
        loadSlot(output, low, frame.valueOffset(value));
        loadSlot(output, high, frame.valueOffset(value) + WORD);
    }

    void store64(StringBuilder output, FrameLayout frame, String low, String high, Value value) {
        storeSlot(output, low, frame.valueOffset(value));
        storeSlot(output, high, frame.valueOffset(value) + WORD);
    }

    void loadSlot(StringBuilder output, String register, int offset) {
        accessSlot(output, "l32i", register, offset, ADDRESS);
    }

    void storeSlot(StringBuilder output, String register, int offset) {
        accessSlot(output, "s32i", register, offset, ADDRESS);
    }

    /** Stores incoming argument register {@code register} while {@link #ADDRESS} may still hold an argument. */
    void spillSlot(StringBuilder output, String register, int offset) {
        accessSlot(output, "s32i", register, offset, A);
    }

    private void accessSlot(StringBuilder output, String instruction, String register, int slotOffset, String address) {
        accessFrame(output, instruction, register, slotBase + slotOffset, address);
    }

    /** Loads the word {@code offset} bytes above {@code a1}, outside the slots: an incoming stack argument. */
    void loadFrameWord(StringBuilder output, String register, int offset) {
        accessFrame(output, "l32i", register, offset, register);
    }

    private void accessFrame(StringBuilder output, String instruction, String register, int offset, String address) {
        if (offset <= MAX_WORD_OFFSET) {
            output.append("    ").append(instruction).append(' ').append(register).append(", a1, ").append(offset)
                    .append('\n');
            return;
        }
        loadImmediate(output, address, offset);
        output.append("    add ").append(address).append(", ").append(address).append(", a1\n")
                .append("    ").append(instruction).append(' ').append(register).append(", ").append(address)
                .append(", 0\n");
    }

    /** {@code register = *(base + offset)}, a word at a small non-negative {@code offset}. */
    void loadWordAt(StringBuilder output, String register, String base, int offset) {
        output.append("    l32i ").append(register).append(", ").append(base).append(", ").append(offset).append('\n');
    }

    /** {@code *(base + offset) = register}, a word at a small non-negative {@code offset}. */
    void storeWordAt(StringBuilder output, String register, String base, int offset) {
        output.append("    s32i ").append(register).append(", ").append(base).append(", ").append(offset).append('\n');
    }

    void loadWord(StringBuilder output, FrameLayout frame, WordSource word, String register) {
        switch (word) {
            case WordSource.FromValue source -> load(output, frame, register, source.value());
            case WordSource.FromValueLow source -> loadSlot(output, register, frame.valueOffset(source.value()));
            case WordSource.FromValueHigh source ->
                    loadSlot(output, register, frame.valueOffset(source.value()) + WORD);
            case WordSource.StringAddress address -> loadStringAddress(output, register, address.literal());
            case WordSource.Immediate immediate -> loadImmediate(output, register, immediate.value());
        }
    }

    /**
     * Calls {@code function} with its first six {@code words} in {@code a10}-{@code a15} and the rest stored at
     * {@code a1+0} upwards, below the slots (see {@link #beginFrame}); the result waits in {@code a10}/{@code a11}.
     */
    void emitCall(StringBuilder output, FrameLayout frame, String function, List<WordSource> words) {
        int stackWords = Math.max(0, words.size() - REGISTER_ARGUMENT_WORDS);
        if (stackWords * WORD > MAX_WORD_OFFSET) {
            throw Architecture.XTENSA.unsupported("a call with " + words.size() + " argument words (" + function + ")");
        }
        for (int index = 0; index < stackWords; index++) {
            loadWord(output, frame, words.get(REGISTER_ARGUMENT_WORDS + index), A);
            storeWordAt(output, A, "a1", index * WORD);
        }
        stackArgumentBytes = Math.max(stackArgumentBytes, stackWords * WORD);
        for (int index = 0; index < Math.min(words.size(), REGISTER_ARGUMENT_WORDS); index++) {
            loadWord(output, frame, words.get(index), argumentRegister(index));
        }
        call(output, function);
    }

    void call(StringBuilder output, String function) {
        output.append("    call8 ").append(function).append('\n');
    }

    /** The caller's register for argument word {@code index}. */
    static String argumentRegister(int index) {
        return "a" + (10 + index);
    }

    /** The callee's register holding incoming argument word {@code index}. */
    static String incomingRegister(int index) {
        return "a" + (2 + index);
    }
}
