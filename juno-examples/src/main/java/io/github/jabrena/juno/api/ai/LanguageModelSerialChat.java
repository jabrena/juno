package io.github.jabrena.juno.api.ai;

import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * Chats with the on-board JLM1 model over the serial port at 115200 baud: type a question and press Enter, and the
 * answer is printed one character at a time as the model generates it. Every 10 seconds it also answers the next of
 * a few fixed questions, so {@code juno:monitor} shows the model working whenever it attaches.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.ai.LanguageModelSerialChat -Djuno.board=arduino-nano-esp32 \
 *   -Djuno.lm.model=../juno-lm/src/main/resources/lm/JUNO.BIN
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class LanguageModelSerialChat {
    private static final LanguageModel MODEL = MCU.getLanguageModel();
    private static final Clock CLOCK = MCU.getClock();
    private static final String[] SELF_TEST = {"What is Juno?", "Which boards does Juno support?", "Hello",
        "How much PSRAM has the Nano ESP32?"};
    private static final byte[] LINE = new byte[LanguageModel.MAX_QUESTION];
    private static final int SELF_TEST_INTERVAL_MS = 10000;

    private LanguageModelSerialChat() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("JLM1: loading JUNO.BIN into PSRAM");
        ModelStatus status = MODEL.begin();
        while (status != ModelStatus.OK) {
            Serial.print("JLM1: model load failed: ");
            Serial.println(LanguageModelStatus.describe(status));
            Delay.millis(5000);
        }
        Serial.print("JLM1: model online, layers=");
        Serial.print(MODEL.layers());
        Serial.print(", width=");
        Serial.println(MODEL.width());

        int length = 0;
        int nextSelfTest = 0;
        int lastSelfTest = CLOCK.millis() - SELF_TEST_INTERVAL_MS;
        while (true) {
            while (Serial.available() > 0) {
                int c = Serial.read();
                if (c == '\n' || c == '\r') {
                    if (length > 0) {
                        Serial.print("[ASK] ");
                        writeBytes(LINE, length);
                        Serial.print(" -> ");
                        answer(MODEL.ask(LINE, length, 0));
                        length = 0;
                    }
                } else if (length < LINE.length) {
                    LINE[length++] = (byte) c;
                }
            }
            if (CLOCK.millis() - lastSelfTest >= SELF_TEST_INTERVAL_MS) {
                selfTest(SELF_TEST[nextSelfTest]);
                nextSelfTest = (nextSelfTest + 1) % SELF_TEST.length;
                lastSelfTest = CLOCK.millis();
            }
            Delay.millis(10);
        }
    }

    private static void selfTest(String question) {
        int started = CLOCK.millis();
        Serial.print("[SELFTEST] ");
        Serial.print(question);
        Serial.print(" -> ");
        answer(MODEL.ask(question, 0));
        Serial.print("[SELFTEST] ");
        Serial.print(CLOCK.millis() - started);
        Serial.println(" ms");
    }

    /** Streams the answer of a question just asked, or prints why it was not asked. */
    private static void answer(ModelStatus status) {
        if (status != ModelStatus.OK) {
            Serial.print("error: ");
            Serial.println(LanguageModelStatus.describe(status));
            return;
        }
        for (int c = MODEL.nextChar(); c != LanguageModel.END; c = MODEL.nextChar()) {
            Serial.write(c);
        }
        Serial.println("");
    }

    private static void writeBytes(byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            Serial.write(bytes[i]);
        }
    }
}
