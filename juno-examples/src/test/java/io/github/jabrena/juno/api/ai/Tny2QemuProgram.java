package io.github.jabrena.juno.api.ai;

import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * The board side of {@code Tny2QemuIT}: loads JUNO.BIN into PSRAM, asks each of {@link #QUESTIONS} and prints
 * {@code question -> answer} per line, once, so the emulator transcript can be compared with the Java engine.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class Tny2QemuProgram {
    /** Read by the test too, so the board and the Java engine always answer the same questions. */
    public static final String[] QUESTIONS = {"What is Juno?", "WHAT IS JUNO", "Which JDK does Juno need?",
        "How much PSRAM does the Nano ESP32 have?", "How much program flash does the Nano ESP32 have?",
        "What is the capital of Portugal?", "Syslog over the moon?"};

    private Tny2QemuProgram() {
    }

    public static void main(String[] args) {
        LanguageModel model = MCU.getLanguageModel();
        Serial.print("load ");
        Serial.println(Tny2ModelLoader.describe(Tny2ModelLoader.load(model)));
        for (String question : QUESTIONS) {
            Serial.print(question);
            Serial.print(" -> ");
            if (model.ask(question, 0) == ModelStatus.OK) {
                for (int c = model.nextChar(); c != LanguageModel.END; c = model.nextChar()) {
                    Serial.write(c);
                }
            }
            Serial.println("");
        }
    }
}
