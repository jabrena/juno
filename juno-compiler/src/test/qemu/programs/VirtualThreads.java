package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;

/**
 * Virtual threads on the preemptive scheduler: classes and lambdas as Runnables, join, sleep, yield, a monitor,
 * stack-slot reuse across many short-lived threads, and garbage collection while other virtual threads are parked
 * with live references on their own stacks. Every printed value is fixed by the joins, so the JVM and the board
 * agree whatever the interleaving.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class VirtualThreads {
    static final Cell LOCK = new Cell(0);
    static int flag;
    static int shared;

    record Cell(int value) {
    }

    /** Churns the arena: each round drops the previous cell and yields, so collections hit parked threads. */
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

    static void bump(int times) {
        for (int i = 0; i < times; i++) {
            synchronized (LOCK) {
                shared++;
            }
        }
    }

    public static void main() throws Exception {
        Accumulator a = new Accumulator(100, 300);
        Accumulator b = new Accumulator(200, 300);
        Accumulator c = new Accumulator(300, 300);
        Thread first = Thread.startVirtualThread(a);
        Thread second = Thread.startVirtualThread(b);
        Thread third = Thread.startVirtualThread(c);
        first.join();
        second.join();
        third.join();
        Serial.println(a.total());
        Serial.println(b.total());
        Serial.println(c.total());
        Serial.println(first.isAlive() ? 1 : 0);

        int captured = 21;
        Thread lambda = Thread.startVirtualThread(() -> {
            flag = captured * 2;
            Serial.println(flag);
        });
        lambda.join();

        Thread left = Thread.startVirtualThread(() -> bump(200));
        Thread right = Thread.startVirtualThread(() -> bump(200));
        left.join();
        right.join();
        Serial.println(shared);

        Thread sleeper = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Serial.println("interrupted");
            }
            flag = 7;
        });
        sleeper.join();
        Serial.println(flag);

        int sum = 0;
        for (int i = 1; i <= 10; i++) {
            int n = i;
            Thread each = Thread.startVirtualThread(() -> flag = n);
            each.join();
            sum += flag;
        }
        Serial.println(sum);
    }
}
