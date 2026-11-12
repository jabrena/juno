package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * Two threads, one per carrier on a dual-core board, allocate linked lists at the same time and check every node they
 * built. The arena is shared, so allocation must be serialized and a collection must stop the other carrier first; a
 * collection that ran while the other carrier kept mutating its list would free live nodes and corrupt them.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class DualCarrierGcStress {
    private static final int ROUNDS = 100;
    private static final int LENGTH = 40;

    static final class Node {
        final int value;
        final Node next;

        Node(int value, Node next) {
            this.value = value;
            this.next = next;
        }
    }

    static final class Worker implements Runnable {
        private final int seed;
        int errors;
        int rounds;

        Worker(int seed) {
            this.seed = seed;
        }

        @Override
        public void run() {
            for (int round = 0; round < ROUNDS; round++) {
                Node head = null;
                for (int index = 0; index < LENGTH; index++) {
                    head = new Node(seed * 1000 + index, head);
                    int[] garbage = new int[8];
                    garbage[0] = index;
                }
                int expected = LENGTH - 1;
                for (Node node = head; node != null; node = node.next) {
                    if (node.value != seed * 1000 + expected) {
                        errors++;
                    }
                    expected--;
                }
                if (expected != -1) {
                    errors++;
                }
                rounds++;
            }
        }
    }

    public static void main() throws Exception {
        Worker first = new Worker(1);
        Worker second = new Worker(2);
        Thread a = Thread.startVirtualThread(first);
        Thread b = Thread.startVirtualThread(second);
        a.join();
        b.join();
        Serial.println(first.rounds + second.rounds);
        Serial.println(first.errors + second.errors);
    }
}
