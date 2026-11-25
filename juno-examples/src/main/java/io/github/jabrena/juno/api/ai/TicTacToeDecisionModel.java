package io.github.jabrena.juno.api.ai;

import io.github.jabrena.juno.ai.DecisionModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Runs the exhaustive tic-tac-toe JEV1 model on a Nano ESP32: {@link DecisionModel#begin()} uses the model the build
 * linked into the firmware, and the first feature is the empty board, so the model prints its opening move.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.ai.TicTacToeDecisionModel -Djuno.board=arduino-nano-esp32 \
 *   -Djuno.dm.model=../juno-ai/juno-models/src/main/resources/dm/TIC_TAC_TOE.JEV
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board(ArduinoNanoEsp32Board.class)
public final class TicTacToeDecisionModel {
    /** One feature per reachable, unfinished position; the first is the empty board. */
    private static final int FEATURES = 4520;
    private static final int EMPTY_BOARD = 0;
    private static final int[] STATE = new int[FEATURES];
    private static final int[] LEGAL_ACTIONS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private TicTacToeDecisionModel() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        DecisionModel model = MCU.getDecisionModel();
        ModelStatus status = model.begin();
        STATE[EMPTY_BOARD] = 1;
        while (true) {
            if (status != ModelStatus.OK) {
                Serial.print("decision model not loaded: ");
                Serial.println(status.ordinal());
            } else {
                Serial.print("model: ");
                Serial.print(model.actions());
                Serial.print(" actions, ");
                Serial.print(model.features());
                Serial.print(" features; opening move: ");
                Serial.println(model.evaluate(STATE, LEGAL_ACTIONS));
            }
            Delay.millis(5000);
        }
    }
}
