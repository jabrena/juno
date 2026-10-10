package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Juno's internal calling convention: arguments are 32-bit words in order (a {@code long}/{@code double}
 * is two, low word first), the first four in r0-r3 and the rest on the stack; a wide result comes back in
 * r0:r1. The caller flattens values with {@link #argumentWords}; the callee's
 * {@link #emitParameterSpill} copies the words into the JVM local slots its bytecode reads.
 */
final class CallConvention {
    private final AsmEmitter asm;

    CallConvention(AsmEmitter asm) {
        this.asm = asm;
    }

    /**
     * Every 32-bit word an argument list passes, in order: a {@code long}/{@code double} contributes its low then
     * high word, matching the JVM's two local slots in the callee's parameter spill.
     */
    static List<WordSource> argumentWords(List<Value> arguments) {
        List<WordSource> words = new ArrayList<>();
        for (Value argument : arguments) {
            if (FrameLayout.isWide(argument.type())) {
                words.add(new WordSource.FromValueLow(argument));
                words.add(new WordSource.FromValueHigh(argument));
            } else {
                words.add(new WordSource.FromValue(argument));
            }
        }
        return words;
    }

    /**
     * Copies incoming words (r0-r3, then the caller's stack area) into the parameters' JVM local slots; a
     * non-static method's receiver is local 0. The caller's stack words sit {@code frame.frameSize() +
     * AsmEmitter.PUSH_BYTES} above this function's {@code sp} after its prologue.
     */
    void emitParameterSpill(StringBuilder output, FrameLayout frame, List<String> parameterTypes,
                            boolean isStatic, Set<Integer> readLocals) {
        int word = 0;
        int slot = 0;
        if (!isStatic) {
            if (readLocals.contains(slot)) {
                spillWord(output, frame, word, frame.localOffset(slot));
            }
            word++;
            slot++;
        }
        for (String type : parameterTypes) {
            if (Descriptor.isDouble(type)) {
                // A double uses two consecutive JVM slots (low word first), matching LoadLocal FLOAT64.
                if (readLocals.contains(slot)) {
                    spillWord(output, frame, word, frame.localOffset(slot));
                    spillWord(output, frame, word + 1, frame.localOffset(slot) + AsmEmitter.WORD);
                }
                word += 2;
                slot += 2;
            } else if (Descriptor.isLong(type)) {
                // A long is two int32 locals, one per JVM slot.
                if (readLocals.contains(slot)) {
                    spillWord(output, frame, word, frame.localOffset(slot));
                }
                word++;
                slot++;
                if (readLocals.contains(slot)) {
                    spillWord(output, frame, word, frame.localOffset(slot));
                }
                word++;
                slot++;
            } else {
                if (readLocals.contains(slot)) {
                    spillWord(output, frame, word, frame.localOffset(slot));
                }
                word++;
                slot++;
            }
        }
    }

    private void spillWord(StringBuilder output, FrameLayout frame, int word, int localOffset) {
        if (word < 4) {
            asm.emitStore(output, "r" + word, localOffset);
        } else {
            asm.emitLoad(output, "r0", frame.frameSize() + AsmEmitter.PUSH_BYTES + (word - 4) * AsmEmitter.WORD);
            asm.emitStore(output, "r0", localOffset);
        }
    }
}
