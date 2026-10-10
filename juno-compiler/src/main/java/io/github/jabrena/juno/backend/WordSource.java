package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.Value;

/** One AAPCS argument word for {@link AsmEmitter#emitShimCall}. */
sealed interface WordSource {
    record FromValue(Value value) implements WordSource {
    }

    record FromValueLow(Value value) implements WordSource {
    }

    record FromValueHigh(Value value) implements WordSource {
    }

    record StringAddress(String literal) implements WordSource {
    }

    record Immediate(int value) implements WordSource {
    }
}
