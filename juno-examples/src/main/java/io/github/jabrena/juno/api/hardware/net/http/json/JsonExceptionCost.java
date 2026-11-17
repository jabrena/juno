package io.github.jabrena.juno.api.hardware.net.http.json;

import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** SPIKE (#285): the arena bytes one thrown and caught exception costs, as JEP 540's failure channel would spend. */
public final class JsonExceptionCost {
    private static final BoardMemory MEMORY = MCU.getBoardMemory();

    static final class JsonValueException extends RuntimeException {
        JsonValueException(String message) {
            super(message);
        }
    }

    static int requireInt(boolean present) {
        if (!present) {
            throw new JsonValueException("missing member");
        }
        return 1;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        while (true) {
            int before = MEMORY.arenaUsedBytes();
            int caught = 0;
            try {
                requireInt(false);
            } catch (JsonValueException e) {
                caught++;
            }
            Serial.print("exception caught ");
            Serial.print(caught);
            Serial.print(" arena ");
            Serial.println(MEMORY.arenaUsedBytes() - before);
            Delay.millis(5000);
        }
    }
}
