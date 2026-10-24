package io.github.jabrena.juno.api.hardware;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Reports how full the board's storage is over USB serial: the program flash, the Juno arena, and the card in a
 * Data Logger Shield (SD chip select on D10) when one is present. Flash and the card are measured once at startup
 * (counting the card's free space scans its whole allocation table) and the whole report repeats every two seconds,
 * so a monitor attached late still sees it.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.StorageOccupancy
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoUnoR3Board.class, ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class})
public final class StorageOccupancy {
    private StorageOccupancy() {
    }

    public static void main(String[] args) {
        final BoardMemory memory = ArduinoUno.getBoardMemory();
        final SdCard sdCard = new DataLoggerShield().getSdCard();
        final int reportPeriodMs = 2000;

        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        int flashUsed = memory.flashUsedBytes();
        int flashCapacity = memory.flashCapacityBytes();
        int sdTotalKiB = -1;
        int sdUsedKiB = -1;
        if (sdCard.begin()) {
            sdTotalKiB = sdCard.totalKiB();
            sdUsedKiB = sdTotalKiB - sdCard.freeKiB();
        }

        while (true) {
            report("flash B ", flashUsed, flashCapacity);
            report("arena B ", memory.arenaUsedBytes(), memory.arenaCapacityBytes());
            if (sdTotalKiB > 0) {
                report("sd KiB ", sdUsedKiB, sdTotalKiB);
            } else {
                Serial.println("sd: no card");
            }
            Delay.millis(reportPeriodMs);
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
