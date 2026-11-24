package io.github.jabrena.juno.api.ai;

import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Loads a JLM1 model from the Nano Connector Carrier's microSD card instead of the bundled one, so a newly trained
 * model only needs copying onto the card: put a JUNO.BIN from {@code -Plm-model} at the card's root. Every 10 seconds
 * it answers {@link #QUESTION} on the serial port at 115200 baud.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class LanguageModelSdCardChat {
    private static final LanguageModel MODEL = MCU.getLanguageModel();
    private static final String MODEL_FILE = "/JUNO.BIN";
    private static final String QUESTION = "What is Juno?";
    private static final int INTERVAL_MS = 10000;

    private LanguageModelSdCardChat() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        SdCard card = new NanoConnectorCarrier(MCU.getGpio()).getSdCard();
        while (!card.begin()) {
            Serial.println("TINY: no microSD card in the Nano Connector Carrier");
            Delay.millis(INTERVAL_MS);
        }
        ModelStatus status = MODEL.begin(card, MODEL_FILE);
        while (true) {
            Serial.print("TINY: ");
            Serial.print(MODEL_FILE);
            Serial.print(" load status ");
            Serial.println(LanguageModelStatus.describe(status));
            if (status == ModelStatus.OK) {
                answer();
            }
            Delay.millis(INTERVAL_MS);
        }
    }

    private static void answer() {
        Serial.print(QUESTION);
        Serial.print(" -> ");
        if (MODEL.ask(QUESTION, 0) == ModelStatus.OK) {
            for (int c = MODEL.nextChar(); c != LanguageModel.END; c = MODEL.nextChar()) {
                Serial.write(c);
            }
        }
        Serial.println("");
    }
}
