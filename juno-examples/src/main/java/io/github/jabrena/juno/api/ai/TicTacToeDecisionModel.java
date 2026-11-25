package io.github.jabrena.juno.api.ai;

import io.github.jabrena.juno.ai.DecisionModel;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Runs the bundled exhaustive tic-tac-toe JEV model on a Nano ESP32. The first feature represents the empty board;
 * the remaining features are reserved for the other reachable positions in the tracked model dataset.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class TicTacToeDecisionModel {
    private static final int FEATURE_COUNT = 4520;
    private static final int[] STATE = new int[FEATURE_COUNT];
    private static final int[] LEGAL_ACTIONS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final DecisionModel MODEL = MCU.getDecisionModel();

    private TicTacToeDecisionModel() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        STATE[0] = 1;
        while (true) {
            Serial.print("tic-tac-toe action: ");
            Serial.println(MODEL.evaluate(STATE, LEGAL_ACTIONS));
            Delay.millis(5000);
        }
    }
}
