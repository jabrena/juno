package io.github.jabrena.juno.backend.thumb2;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.IntrinsicTarget;
import io.github.jabrena.juno.backend.WordSource;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Optional;

/** {@link IntrinsicTarget} for Thumb-2: AAPCS words in r0-r3 then on the stack, results in r0 (and r1). */
record Thumb2IntrinsicTarget(AsmEmitter asm) implements IntrinsicTarget {
    @Override
    public void emitShimCall(StringBuilder output, FrameLayout frame, String function, List<WordSource> words) {
        asm.emitShimCall(output, frame, function, words);
    }

    @Override
    public void storeResult(StringBuilder output, FrameLayout frame, Value target) {
        asm.store(output, frame, "r0", target);
    }

    @Override
    public void storeWideResult(StringBuilder output, FrameLayout frame, Value target) {
        asm.store64(output, frame, "r0", "r1", target);
    }

    @Override
    public void copy(StringBuilder output, FrameLayout frame, Value source, Value target) {
        asm.load(output, frame, "r0", source);
        asm.store(output, frame, "r0", target);
    }

    @Override
    public void storeImmediate(StringBuilder output, FrameLayout frame, int value, Value target) {
        asm.emitLoadImmediate(output, "r0", value);
        asm.store(output, frame, "r0", target);
    }

    @Override
    public void storeObjectWord(StringBuilder output, FrameLayout frame, Value object, int offset, Value value) {
        asm.load(output, frame, "r0", object);
        asm.load(output, frame, "r1", value);
        output.append("    str r1, [r0, #").append(offset).append("]\n");
    }

    @Override
    public void loadObjectWord(StringBuilder output, FrameLayout frame, Value object, int offset,
                               Optional<Value> target) {
        asm.load(output, frame, "r0", object);
        output.append("    ldr r0, [r0, #").append(offset).append("]\n");
        target.ifPresent(value -> asm.store(output, frame, "r0", value));
    }

    @Override
    public CompileException unsupported(String detail) {
        return Thumb2AsmBackend.unsupported(detail);
    }
}
