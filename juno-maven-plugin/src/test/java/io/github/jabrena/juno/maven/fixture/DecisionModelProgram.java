package io.github.jabrena.juno.maven.fixture;

import io.github.jabrena.juno.ai.DecisionModel;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.Serial;

/** A program that links its decision model with {@code begin()}, so it builds only with {@code juno.dm.model}. */
public final class DecisionModelProgram {
    private static final int[] STATE = new int[3];
    private static final int[] LEGAL_ACTIONS = {0, 1};

    private DecisionModelProgram() {
    }

    public static void main(String[] args) {
        DecisionModel model = MCU.getDecisionModel();
        model.begin();
        Serial.println(model.evaluate(STATE, LEGAL_ACTIONS));
    }
}
