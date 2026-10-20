package io.github.jabrena.juno.backend.xtensa;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.IntrinsicTarget;
import io.github.jabrena.juno.backend.WordSource;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Optional;

import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.A;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.B;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.C;

/**
 * {@link IntrinsicTarget} for Xtensa: the table's AAPCS word layout (a two-word value starting at an even word) is
 * also the windowed ABI's register-pair rule, so word {@code i} goes in {@code a(10+i)}; results come back in
 * {@code a10}/{@code a11}.
 */
record XtensaIntrinsicTarget(XtensaEmitter xtensa) implements IntrinsicTarget {
    @Override
    public void emitShimCall(StringBuilder output, FrameLayout frame, String function, List<WordSource> words) {
        xtensa.emitCall(output, frame, function, words);
    }

    @Override
    public void storeResult(StringBuilder output, FrameLayout frame, Value target) {
        xtensa.store(output, frame, "a10", target);
    }

    @Override
    public void storeWideResult(StringBuilder output, FrameLayout frame, Value target) {
        xtensa.store64(output, frame, "a10", "a11", target);
    }

    @Override
    public void copy(StringBuilder output, FrameLayout frame, Value source, Value target) {
        xtensa.load(output, frame, A, source);
        xtensa.store(output, frame, A, target);
    }

    @Override
    public void storeImmediate(StringBuilder output, FrameLayout frame, int value, Value target) {
        xtensa.loadImmediate(output, A, value);
        xtensa.store(output, frame, A, target);
    }

    @Override
    public void storeObjectWord(StringBuilder output, FrameLayout frame, Value object, int offset, Value value) {
        xtensa.load(output, frame, A, object);
        xtensa.load(output, frame, B, value);
        xtensa.storeWordAt(output, B, A, offset);
    }

    @Override
    public void loadObjectWord(StringBuilder output, FrameLayout frame, Value object, int offset,
                               Optional<Value> target) {
        xtensa.load(output, frame, A, object);
        xtensa.loadWordAt(output, C, A, offset);
        target.ifPresent(value -> xtensa.store(output, frame, C, value));
    }

    @Override
    public CompileException unsupported(String detail) {
        return Architecture.XTENSA.unsupported(detail);
    }
}
