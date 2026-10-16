package io.github.jabrena.juno.api;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Reports how full the board's storage is over USB serial: the program flash, the Juno arena, and an SD card
 * on D10 when one is present. Flash and the card are measured once at startup (counting the card's free space
 * scans its whole allocation table) and the whole report repeats every two seconds, so a monitor attached late
 * still sees it.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.StorageOccupancy
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoUnoR3.class, ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class StorageOccupancy {
    private static final int REPORT_PERIOD_MS = 2000;

    private StorageOccupancy() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        int flashUsed = Memory.flashUsedBytes();
        int flashCapacity = Memory.flashCapacityBytes();
        int sdTotalKiB = -1;
        int sdUsedKiB = -1;
        if (SdCard.begin()) {
            sdTotalKiB = SdCard.totalKiB();
            sdUsedKiB = sdTotalKiB - SdCard.freeKiB();
        }

        while (true) {
            report("flash B ", flashUsed, flashCapacity);
            report("arena B ", Memory.arenaUsedBytes(), Memory.arenaCapacityBytes());
            if (sdTotalKiB > 0) {
                report("sd KiB ", sdUsedKiB, sdTotalKiB);
            } else {
                Serial.println("sd: no card");
            }
            Delay.millis(REPORT_PERIOD_MS);
        }
    }

    /** Prints {@code used / capacity (percent%)}; the percentage avoids overflow on a large card's KiB count. */
    private static void report(String label, int used, int capacity) {
        Serial.print(label);
        Serial.print(used);
        Serial.print(" / ");
        Serial.print(capacity);
        if (capacity > 0) {
            int percent = capacity > 20_000_000 ? used / (capacity / 100) : used * 100 / capacity;
            Serial.print(" (");
            Serial.print(percent);
            Serial.print("%)");
        }
        Serial.println("");
    }
}
