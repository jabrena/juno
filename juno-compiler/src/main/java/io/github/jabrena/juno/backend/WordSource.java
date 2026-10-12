package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.List;

/** One argument word of a call: Juno's calling convention passes every argument as 32-bit words, in order. */
public sealed interface WordSource {
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
