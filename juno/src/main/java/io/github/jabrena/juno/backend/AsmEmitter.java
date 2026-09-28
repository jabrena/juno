package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Map;

/**
 * The instruction-level building blocks every lowering shares: moving words between registers and a
 * method's fixed stack slots (see {@link FrameLayout}), materializing immediates and string-literal
 * addresses, calling {@code extern "C"} runtime-shim functions under AAPCS, and minting unique local
 * labels. Registers are only ever scratch within one instruction's codegen.
 */
final class AsmEmitter {
    static final int WORD = 4;
    /** Bytes {@code push {r4-r11, lr}} reserves — every prologue/epilogue is built around this. */
    static final int PUSH_BYTES = 9 * WORD;

    private final Map<String, String> stringLiteralSymbols;
    private int labelCounter;

    AsmEmitter(Map<String, String> stringLiteralSymbols) {
        this.stringLiteralSymbols = stringLiteralSymbols;
    }

    static int roundUp(int value, int multiple) {
        return ((value + multiple - 1) / multiple) * multiple;
    }

    /** A fresh assembler-local label, unique across the whole generated file. */
    String newLabel(String prefix) {
        return prefix + (labelCounter++);
    }

    /** Loads the address of {@code literal}'s {@code .rodata} symbol (see {@link ProgramLayout}). */
    void emitStringAddress(StringBuilder output, String register, String literal) {
        output.append("    ldr ").append(register).append(", =")
                .append(stringLiteralSymbols.get(literal)).append('\n');
    }

    /** {@code movw}/{@code movt} loads any 32-bit bit pattern; {@code movs} is just a shorter encoding. */
    void emitLoadImmediate(StringBuilder output, String register, int value) {
        if (value >= 0 && value <= 255) {
            output.append("    movs ").append(register).append(", #").append(value).append('\n');
            return;
        }
        int low16 = value & 0xFFFF;
        int high16 = (value >>> 16) & 0xFFFF;
        output.append("    movw ").append(register).append(", #").append(low16).append('\n');
        if (high16 != 0) {
            output.append("    movt ").append(register).append(", #").append(high16).append('\n');
        }
    }

    void load(StringBuilder output, FrameLayout frame, String register, Value value) {
        emitLoad(output, register, frame.valueOffset(value));
    }

    void store(StringBuilder output, FrameLayout frame, String register, Value value) {
        emitStore(output, register, frame.valueOffset(value));
    }

    /**
     * Loads/stores an 8-byte {@link JunoType#INT64}/{@link JunoType#FLOAT64} value as two consecutive
     * words (low word first, at the lower offset/address — matching AAPCS's own 64-bit register-pair
     * convention and real little-endian {@code int64_t}/{@code double} memory layout, so these slots'
     * bytes are bit-identical to what a real C++ variable of the same value would hold).
     */
    void load64(StringBuilder output, FrameLayout frame, String lowRegister, String highRegister, Value value) {
        emitLoad(output, lowRegister, frame.valueOffset(value));
        emitLoad(output, highRegister, frame.valueOffset(value) + WORD);
    }

    void store64(StringBuilder output, FrameLayout frame, String lowRegister, String highRegister, Value value) {
        emitStore(output, lowRegister, frame.valueOffset(value));
        emitStore(output, highRegister, frame.valueOffset(value) + WORD);
    }

    private void loadWord(StringBuilder output, FrameLayout frame, WordSource source, String register, int extraSpOffset) {
        switch (source) {
            case WordSource.FromValue from -> emitLoad(output, register, frame.valueOffset(from.value()) + extraSpOffset);
            case WordSource.FromValueLow from -> emitLoad(output, register, frame.valueOffset(from.value()) + extraSpOffset);
            case WordSource.FromValueHigh from ->
                    emitLoad(output, register, frame.valueOffset(from.value()) + WORD + extraSpOffset);
            case WordSource.StringAddress address -> emitStringAddress(output, register, address.literal());
            case WordSource.Immediate immediate -> emitLoadImmediate(output, register, immediate.value());
        }
    }

    /**
     * Calls an {@code extern "C"} runtime-shim function taking exactly {@code words.size()} plain
     * 32-bit AAPCS argument words (register args 0-3, any beyond that on a transient stack area) —
     * the same overflow-to-stack mechanism as {@link CortexM4AsmBackend}'s calls between generated
     * methods, generalized to sources that aren't necessarily a single whole {@link Value} (a wide
     * {@code long}/{@code double} argument supplies two of these, one low-word source and one
     * high-word source).
     */
    void emitShimCall(StringBuilder output, FrameLayout frame, String functionName, List<WordSource> words) {
        int total = words.size();
        int extra = Math.max(0, total - 4);
        int reserved = roundUp(extra * WORD, 8);
        if (reserved > 0) {
            output.append("    sub sp, sp, #").append(reserved).append('\n');
            for (int i = 4; i < total; i++) {
                loadWord(output, frame, words.get(i), "r0", reserved);
                emitStore(output, "r0", (i - 4) * WORD);
            }
        }
        for (int i = 0; i < Math.min(4, total); i++) {
            loadWord(output, frame, words.get(i), "r" + i, reserved);
        }
        output.append("    bl ").append(functionName).append('\n');
        if (reserved > 0) {
            output.append("    add sp, sp, #").append(reserved).append('\n');
        }
    }

    /**
     * {@code ldr Rd,[sp,#imm]} only encodes offsets up to 4095; a method with enough live
     * values/locals exceeds that easily. Past that, compute the address
     * in r12 (AAPCS "ip", always caller-saved/scratch, never used to hold a Java value here) instead.
     */
    void emitLoad(StringBuilder output, String destinationRegister, int offset) {
        if (offset <= 4095) {
            output.append("    ldr ").append(destinationRegister).append(", [sp, #").append(offset).append("]\n");
        } else {
            emitLoadImmediate(output, "r12", offset);
            output.append("    add r12, r12, sp\n")
                    .append("    ldr ").append(destinationRegister).append(", [r12]\n");
        }
    }

    /** See {@link #emitLoad}. */
    void emitStore(StringBuilder output, String sourceRegister, int offset) {
        if (offset <= 4095) {
            output.append("    str ").append(sourceRegister).append(", [sp, #").append(offset).append("]\n");
        } else {
            emitLoadImmediate(output, "r12", offset);
            output.append("    add r12, r12, sp\n")
                    .append("    str ").append(sourceRegister).append(", [r12]\n");
        }
    }
}
