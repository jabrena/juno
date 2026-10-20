package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * Everything a thread preempted part-way through an expression must get back intact. Several threads run long,
 * loop-heavy computations at once, so the preemption tick strikes them between arbitrary instructions: in the middle
 * of floating-point sums (the caller-saved FPU registers and FPSCR), compare-and-branch chains (the flags), freshly
 * allocated objects that so far live only in a register while another thread collects the arena (the stack scan of a
 * preempted thread), throw/catch (the pending exception of each thread), and 64-bit field stores that another
 * thread reads concurrently (a {@code volatile long} must never be seen half-written). Each result is fixed, so the
 * JVM and the board print the same lines whatever the interleaving.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class PreemptionStress {
    static volatile long wide;
    static volatile boolean writing;

    record Node(int value, Node next) {
    }

    static final class FloatWork implements Runnable {
        private final float step;
        private float floats;
        private double doubles;

        FloatWork(float step) {
            this.step = step;
        }

        @Override
        public void run() {
            float sum = 0.0f;
            double precise = 0.0;
            for (int i = 1; i <= 4000; i++) {
                float term = i * step;
                if (term > 100.0f) {
                    sum += term / 8.0f;
                } else {
                    sum += term;
                }
                precise += Math.sqrt(i) * step;
            }
            floats = sum;
            doubles = precise;
        }
    }

    static final class Branches implements Runnable {
        private final int seed;
        private int result;

        Branches(int seed) {
            this.seed = seed;
        }

        @Override
        public void run() {
            int x = seed;
            int below = 0;
            int above = 0;
            for (int i = 0; i < 20000; i++) {
                x = x * 1103515245 + 12345;
                int bits = (x >>> 16) & 0x7FFF;
                if (bits < 0x2000) {
                    below++;
                } else if (bits >= 0x6000) {
                    above++;
                } else if ((bits & 1) == 0) {
                    below--;
                } else {
                    above--;
                }
            }
            result = below * 31 + above;
        }
    }

    static final class Lists implements Runnable {
        private final int base;
        private int checksum;

        Lists(int base) {
            this.base = base;
        }

        @Override
        public void run() {
            int total = 0;
            for (int round = 0; round < 40; round++) {
                Node head = null;
                for (int i = 0; i < 60; i++) {
                    head = new Node(base + round + i, head);
                }
                for (Node node = head; node != null; node = node.next()) {
                    total += node.value();
                }
            }
            checksum = total;
        }
    }

    static final class Throwing implements Runnable {
        private final int divisor;
        private int caught;

        Throwing(int divisor) {
            this.divisor = divisor;
        }

        @Override
        public void run() {
            for (int i = 0; i < 2000; i++) {
                try {
                    check(i, divisor);
                } catch (IllegalStateException expected) {
                    caught++;
                }
            }
        }

        static void check(int value, int divisor) {
            if (value % divisor == 0) {
                throw new IllegalStateException("multiple");
            }
        }
    }

    static int tornReads() {
        writing = true;
        // Unrolled, so the writer spends its time storing rather than on its loop backedge.
        Thread writer = Thread.startVirtualThread(() -> {
            while (writing) {
                wide = 0L;
                wide = -1L;
                wide = 0L;
                wide = -1L;
                wide = 0L;
                wide = -1L;
                wide = 0L;
                wide = -1L;
            }
        });
        int torn = 0;
        for (int i = 0; i < 100000; i++) {
            long seen = wide;
            if ((int) seen != (int) (seen >>> 32)) {
                torn++;
            }
        }
        writing = false;
        try {
            writer.join();
        } catch (InterruptedException interrupted) {
            torn = -1;
        }
        return torn;
    }

    public static void main() throws Exception {
        FloatWork small = new FloatWork(0.25f);
        FloatWork large = new FloatWork(1.5f);
        Branches branches = new Branches(7);
        Thread a = Thread.startVirtualThread(small);
        Thread b = Thread.startVirtualThread(large);
        Thread c = Thread.startVirtualThread(branches);
        a.join();
        b.join();
        c.join();
        Serial.println((long) (small.floats * 100.0));
        Serial.println((long) (large.floats * 100.0));
        Serial.println((long) (small.doubles * 1000.0));
        Serial.println((long) (large.doubles * 1000.0));
        Serial.println(branches.result);

        Lists first = new Lists(1);
        Lists second = new Lists(1000);
        Lists third = new Lists(50000);
        Thread d = Thread.startVirtualThread(first);
        Thread e = Thread.startVirtualThread(second);
        Thread f = Thread.startVirtualThread(third);
        d.join();
        e.join();
        f.join();
        Serial.println(first.checksum);
        Serial.println(second.checksum);
        Serial.println(third.checksum);

        Throwing threes = new Throwing(3);
        Throwing sevens = new Throwing(7);
        Thread g = Thread.startVirtualThread(threes);
        Thread h = Thread.startVirtualThread(sevens);
        g.join();
        h.join();
        Serial.println(threes.caught);
        Serial.println(sevens.caught);

        Serial.println(tornReads());
    }
}
