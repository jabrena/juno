package io.github.jabrena.juno.backend.avr;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.IntrinsicTarget;
import io.github.jabrena.juno.backend.WordSource;
import io.github.jabrena.juno.backend.runtime.AvrShimThunks;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link IntrinsicTarget} for AVR: every runtime call goes through a generated shim thunk (see {@link AvrShimThunks}),
 * results come back in r22-r25 (r18-r25 for two words). Arduino core functions the shared intrinsic table names
 * directly take 8- and 16-bit parameters on this core, so they are reached through {@code juno_*} wrappers with
 * Java-width parameters instead.
 */
record AvrIntrinsicTarget(AvrEmitter avr) implements IntrinsicTarget {
    private static final Map<String, String> CORE_WRAPPERS = Map.of(
            "pinMode", "juno_pin_mode",
            "digitalWrite", "juno_digital_write",
            "digitalRead", "juno_digital_read",
            "analogRead", "juno_analog_read",
            "analogWrite", "juno_analog_write",
            "millis", "juno_millis",
            "micros", "juno_micros");

    @Override
    public void emitShimCall(StringBuilder output, FrameLayout frame, String function, List<WordSource> words) {
        avr.emitThunkCall(output, frame, CORE_WRAPPERS.getOrDefault(function, function), words);
    }

    @Override
    public void storeResult(StringBuilder output, FrameLayout frame, Value target) {
        avr.store(output, frame, AvrEmitter.A, target);
    }

    @Override
    public void storeWideResult(StringBuilder output, FrameLayout frame, Value target) {
        avr.store64(output, frame, AvrEmitter.B, AvrEmitter.A, target);
    }

    @Override
    public void copy(StringBuilder output, FrameLayout frame, Value source, Value target) {
        avr.load(output, frame, AvrEmitter.A, source);
        avr.store(output, frame, AvrEmitter.A, target);
    }

    @Override
    public void storeImmediate(StringBuilder output, FrameLayout frame, int value, Value target) {
        avr.loadImmediate(output, AvrEmitter.A, value);
        avr.store(output, frame, AvrEmitter.A, target);
    }

    @Override
    public void storeObjectWord(StringBuilder output, FrameLayout frame, Value object, int offset, Value value) {
        avr.load(output, frame, AvrEmitter.A, value);
        avr.loadPointer(output, frame, object);
        avr.storeToZ(output, AvrEmitter.A, offset, AvrEmitter.WORD);
    }

    @Override
    public void loadObjectWord(StringBuilder output, FrameLayout frame, Value object, int offset,
                               Optional<Value> target) {
        avr.loadPointer(output, frame, object);
        avr.loadFromZ(output, AvrEmitter.A, offset, AvrEmitter.WORD);
        target.ifPresent(value -> avr.store(output, frame, AvrEmitter.A, value));
    }

    @Override
    public CompileException unsupported(String detail) {
        return Architecture.AVR.unsupported(detail);
    }
}
