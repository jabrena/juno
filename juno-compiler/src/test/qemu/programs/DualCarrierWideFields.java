package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * A volatile long written on one carrier and read on the other must never be seen half-written. The long sits after an
 * int, so a wide access that missed the field's offset would also show up: it would tear the value and clobber the int.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class DualCarrierWideFields {
    private static final int READS = 20_000;

    static final class Cell {
        volatile int started;
        volatile long value;
        volatile int writing;
    }

    public static void main() throws Exception {
        Cell cell = new Cell();
        cell.writing = 1;
        Thread writer = Thread.startVirtualThread(() -> {
            cell.value = -1L;
            cell.started = 1;
            while (cell.writing == 1) {
                cell.value = 0L;
                cell.value = -1L;
            }
        });
        while (cell.started == 0) {
            Thread.yield();
        }
        int torn = 0;
        for (int read = 0; read < READS; read++) {
            long observed = cell.value;
            if ((int) observed != (int) (observed >>> 32)) {
                torn++;
            }
        }
        cell.writing = 0;
        writer.join();
        Serial.println(torn);
        Serial.println(cell.started);
    }
}
