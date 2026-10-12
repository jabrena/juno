package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;

/** Hands out one method's IR values, numbered in creation order. */
final class FreshValues {
    private int next;

    Value int32() {
        return of(JunoType.INT32);
    }

    Value int64() {
        return of(JunoType.INT64);
    }

    Value float32() {
        return of(JunoType.FLOAT32);
    }

    Value float64() {
        return of(JunoType.FLOAT64);
    }

    Value of(JunoType type) {
        return new Value(next++, type);
    }

    /** How many values have been handed out. */
    int count() {
        return next;
    }
}
