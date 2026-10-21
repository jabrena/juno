package io.github.jabrena.juno.api.hardware.device.sd;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.BoardModel;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;

/**
 * Exercises every {@link SdCard} file operation end to end: creates a file by appending its first
 * line, appends a second line, reads the whole file back, deletes it, then confirms the deletion —
 * each step reported over USB serial so the behavior can be checked remotely with a serial monitor
 * instead of pulling the card to inspect it by hand.
 *
 * <p>On an UNO R4 WiFi or UNO Q, wire a standard SPI SD card breakout with its chip-select line on D10 (the
 * default {@link SdCard#begin()} pin). On a Nano ESP32, plug it into a {@link NanoConnectorCarrier}, whose microSD
 * slot selects the card on D4. Upload, then watch the result:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.device.sd.SdFileOperations -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoNanoEsp32.class})
public final class SdFileOperations {
    private static final String PATH = "juno-sd-demo.txt";

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        if (!SdCard.begin(chipSelectPin())) {
            Serial.println("SD card initialization failed");
            return;
        }
        Serial.println("SD card ready");

        SdCard.remove(PATH); // start from a clean slate on every run
        report("exists before create", SdCard.exists(PATH));

        report("create (append line 1)", SdCard.appendLine(PATH, "line 1"));
        report("append line 2", SdCard.appendLine(PATH, "line 2"));
        report("exists after append", SdCard.exists(PATH));

        Serial.println("reading back:");
        readBack();

        report("remove", SdCard.remove(PATH));
        report("exists after remove", SdCard.exists(PATH));

        Serial.println("done");
        while (true) {
            Delay.millis(1000);
        }
    }

    /** D10 for a breakout on the UNO boards; of the boards above, only the Nano ESP32 is neither. */
    private static int chipSelectPin() {
        BoardModel model = MCU.getBoardModel();
        return model.isR4() || model.isQ() ? Gpio.getD10() : new NanoConnectorCarrier().getSdChipSelect();
    }

    private static void report(String label, boolean value) {
        Serial.print(label);
        Serial.print(": ");
        Serial.println(value ? "true" : "false");
    }

    private static void readBack() throws IOException {
        InputStream file = SdCard.open(PATH);
        if (file == null) {
            Serial.println("  (could not open)");
            return;
        }
        StringBuilder line = new StringBuilder(32);
        while (file.available() > 0) {
            char next = (char) file.read();
            if (next == '\n') {
                Serial.print("  ");
                Serial.println(line.toString());
                line = new StringBuilder(32);
            } else if (next != '\r') {
                line.append(next);
            }
        }
        file.close();
    }
}
