package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.util.concurrent.StructuredTaskScope;

/**
 * The cooperative scheduler under StructuredTaskScope: classes and lambdas as Runnables, sleep, yield, stack-slot
 * reuse, and garbage collection while other subtasks are parked with live references on their own stacks. Every
 * printed value is fixed by the joins, so the JVM and the board agree whatever the interleaving.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class TaskScheduling {
    static int flag;

    record Cell(int value) {
    }

    /** Churns the arena: each round drops the previous cell and yields, so collections hit parked subtasks. */
    static final class Accumulator implements Runnable {
        private final int base;
        private final int rounds;
        private int total;

        Accumulator(int base, int rounds) {
            this.base = base;
            this.rounds = rounds;
        }

        @Override
        public void run() {
            Cell cell = new Cell(base);
            for (int i = 0; i < rounds; i++) {
                cell = new Cell(cell.value() + i);
                Thread.yield();
            }
            total = cell.value();
        }

        int total() {
            return total;
        }
    }

    public static void main() throws Exception {
        Accumulator a = new Accumulator(100, 300);
        Accumulator b = new Accumulator(200, 300);
        Accumulator c = new Accumulator(300, 300);
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(a);
            scope.fork(b);
            scope.fork(c);
            scope.join();
        }
        Serial.println(a.total());
        Serial.println(b.total());
        Serial.println(c.total());

        int captured = 21;
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> {
                flag = captured * 2;
                Serial.println(flag);
            });
            scope.join();
        }

        for (int round = 0; round < 6; round++) {
            Accumulator worker = new Accumulator(round, 20);
            try (var scope = StructuredTaskScope.open()) {
                scope.fork(worker);
                scope.join();
            }
            Serial.println(worker.total());
        }

        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
                flag = 7;
            });
            scope.join();
        }
        Serial.println(flag);
        Serial.println(99);
    }
}
