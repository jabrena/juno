package io.github.jabrena.juno.maven.fixture;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.Serial;

/** A program that links its language model with {@code begin()}, so it builds only with {@code juno.lm.model}. */
public final class LanguageModelProgram {
    private LanguageModelProgram() {
    }

    public static void main(String[] args) {
        Serial.println(MCU.getLanguageModel().begin().ordinal());
    }
}
