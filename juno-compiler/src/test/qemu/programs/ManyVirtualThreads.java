package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;
import java.util.concurrent.StructuredTaskScope;

/**
 * More virtual threads and subtasks alive at once than the old four scheduler slots: every thread parks in
 * {@code Thread.sleep} with live locals and a deep call chain while the others run, so each one is frozen off the
 * carrier and thawed back several times, and collections run while they are parked. Every printed value is fixed by
 * the joins, so the JVM and the board agree whatever the interleaving.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoNanoEsp32Board.class})
public final class ManyVirtualThreads {
    static final int THREADS = 10;
    static final int[] RESULTS = new int[THREADS];

    record Cell(int value) {
    }

    /** A few frames deep, holding a reference across every sleep. */
    static int descend(int id, int depth) throws InterruptedException {
        Cell cell = new Cell(id * 10 + depth);
        if (depth == 0) {
            int total = 0;
            for (int round = 0; round < 3; round++) {
                Thread.sleep(2);
                total += new Cell(round).value();
            }
            return cell.value() + total;
        }
        return cell.value() + descend(id, depth - 1);
    }

    public static void main() throws Exception {
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            int id = i;
            threads[i] = Thread.startVirtualThread(() -> {
                try {
                    RESULTS[id] = descend(id, 1);
                } catch (InterruptedException interrupted) {
                    RESULTS[id] = -1;
                }
            });
        }
        for (int i = 0; i < THREADS; i++) {
            threads[i].join();
        }
        int sum = 0;
        for (int i = 0; i < THREADS; i++) {
            Serial.println(RESULTS[i]);
            sum += RESULTS[i];
        }
        Serial.println(sum);

        int[] forked = new int[8];
        try (var scope = StructuredTaskScope.open()) {
            for (int i = 0; i < forked.length; i++) {
                int id = i;
                scope.fork(() -> {
                    try {
                        forked[id] = descend(id, 1);
                    } catch (InterruptedException interrupted) {
                        forked[id] = -1;
                    }
                });
            }
            scope.join();
        }
        int forkedSum = 0;
        for (int value : forked) {
            forkedSum += value;
        }
        Serial.println(forkedSum);
    }
}
