package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * java.lang.Thread: classes and lambdas as Runnables, join, sleep, yield, daemon threads, stack-slot reuse, and
 * garbage collection while other threads are parked with live references on their own stacks. Every printed value
 * is fixed by joins, so the JVM and the board agree whatever the interleaving.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Threads {
    static int flag;
    static int ticks;

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

    static final class Waiter implements Runnable {
        private final Thread other;
        private int seen;

        Waiter(Thread other) {
            this.other = other;
        }

        @Override
        public void run() {
            try {
                other.join();
            } catch (InterruptedException e) {
                seen = -1;
                return;
            }
            seen = other.isAlive() ? 1 : 2;
        }

        int seen() {
            return seen;
        }
    }

    public static void main() throws InterruptedException {
        Accumulator a = new Accumulator(100, 300);
        Accumulator b = new Accumulator(200, 300);
        Accumulator c = new Accumulator(300, 300);
        Thread ta = new Thread(a);
        Thread tb = new Thread(b);
        Thread tc = new Thread(c);
        Serial.println(ta.isAlive() ? 1 : 0);
        ta.start();
        tb.start();
        tc.start();
        ta.join();
        tb.join();
        tc.join();
        Serial.println(a.total());
        Serial.println(b.total());
        Serial.println(c.total());
        Serial.println(tc.isAlive() ? 1 : 0);

        int captured = 21;
        Thread lambda = new Thread(() -> {
            flag = captured * 2;
            Serial.println(flag);
        });
        lambda.start();
        lambda.join();

        for (int round = 0; round < 6; round++) {
            Accumulator worker = new Accumulator(round, 20);
            Thread thread = new Thread(worker);
            thread.start();
            thread.join();
            Serial.println(worker.total());
        }

        Thread sleeper = new Thread(() -> {
            flag = 7;
        });
        sleeper.start();
        Thread.sleep(50);
        Serial.println(flag);

        Accumulator slow = new Accumulator(0, 50);
        Thread slowThread = new Thread(slow);
        Waiter waiter = new Waiter(slowThread);
        Thread waiting = new Thread(waiter);
        waiting.start();
        slowThread.start();
        waiting.join();
        Serial.println(waiter.seen());
        Serial.println(slow.total());

        Thread ticker = new Thread(() -> {
            while (true) {
                ticks++;
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        ticker.setDaemon(true);
        ticker.start();
        Thread.sleep(20);
        Serial.println(ticks > 0 ? 1 : 0);
        Serial.println(99);
    }
}
