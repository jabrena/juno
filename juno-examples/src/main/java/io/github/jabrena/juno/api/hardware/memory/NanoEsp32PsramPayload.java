package io.github.jabrena.juno.api.hardware.memory;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Psram;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Stores a caller-owned UTF-8 payload in PSRAM while keeping the Java heap buffer small and reusable. */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoEsp32PsramPayload {
    private static final int PAYLOAD_SIZE = 8192;
    private static final int CHUNK_SIZE = 128;
    private static final byte[] CHUNK = new byte[CHUNK_SIZE];
    private static final byte[] UTF8_PATTERN = {74, 117, 110, 111, 32, 80, 83, 82, 65, 77, 32, -49, -128};

    private NanoEsp32PsramPayload() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);
        int handle = Psram.allocate(PAYLOAD_SIZE);
        if (handle < 0) {
            Serial.print("PSRAM allocation failed: ");
            Serial.println(handle);
            return;
        }

        boolean success = fillPayload(handle);
        Serial.print("PSRAM payload bytes: ");
        Serial.println(success ? PAYLOAD_SIZE : -1);
        Psram.release(handle);
        while (true) {
            Delay.millis(1000);
        }
    }

    private static boolean fillPayload(int handle) {
        for (int offset = 0; offset < PAYLOAD_SIZE; offset = offset + CHUNK_SIZE) {
            for (int index = 0; index < CHUNK_SIZE; index = index + 1) {
                CHUNK[index] = UTF8_PATTERN[(offset + index) % UTF8_PATTERN.length];
            }
            if (Psram.write(handle, offset, CHUNK, 0, CHUNK_SIZE) != CHUNK_SIZE) {
                return false;
            }
        }
        return true;
    }
}
