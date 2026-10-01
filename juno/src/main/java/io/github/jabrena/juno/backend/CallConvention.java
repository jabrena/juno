package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Juno's internal calling convention: arguments are 32-bit words in order (a {@code long}/{@code double}
 * is two, low word first), the first four in r0-r3 and the rest on the stack; a wide result comes back in
 * r0:r1. The caller flattens values with {@link #argumentWordOffsets}; the callee's
 * {@link #emitParameterSpill} copies the words into the JVM local slots its bytecode reads.
 */
final class CallConvention {
    private final AsmEmitter asm;

    CallConvention(AsmEmitter asm) {
        this.asm = asm;
    }

    /**
     * The frame offset of every 32-bit word an argument list passes, in order: a {@code long}/{@code double}
     * contributes its low then high word, matching the JVM's two local slots in the callee's parameter spill.
     */
    static List<Integer> argumentWordOffsets(FrameLayout frame, List<Value> arguments) {
        List<Integer> words = new ArrayList<>();
        for (Value argument : arguments) {
            words.add(frame.valueOffset(argument));
            if (FrameLayout.isWide(argument.type())) {
                words.add(frame.valueOffset(argument) + AsmEmitter.WORD);
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
                                    boolean isStatic) {
        int word = 0;
        int slot = 0;
        if (!isStatic) {
            spillWord(output, frame, word++, frame.localOffset(slot++));
        }
        for (String type : parameterTypes) {
            if (Descriptor.isDouble(type)) {
                // A double is one 8-byte local (low word first), like a LoadLocal of FLOAT64 reads it.
                spillWord(output, frame, word++, frame.localOffset(slot));
                spillWord(output, frame, word++, frame.localOffset(slot) + AsmEmitter.WORD);
                slot += 2;
            } else if (Descriptor.isLong(type)) {
                // A long is two int32 locals, one per JVM slot.
                spillWord(output, frame, word++, frame.localOffset(slot++));
                spillWord(output, frame, word++, frame.localOffset(slot++));
            } else {
                spillWord(output, frame, word++, frame.localOffset(slot++));
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
