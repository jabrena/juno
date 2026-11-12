package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * ScopedValue.run calls its Java body from inside a runtime call. The body forks subtasks, which land on the other
 * carrier and keep entering the runtime (scoped lookups, allocations), and joins them; the body must not keep the
 * runtime lock while it waits, or the subtasks never finish.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class DualCarrierScopedRun {
    private static final ScopedValue<int[]> FACTOR = ScopedValue.newInstance();
    static final int[] RESULTS = new int[2];

    static final class Multiplier implements Runnable {
        private final int slot;

        Multiplier(int slot) {
            this.slot = slot;
        }

        @Override
        public void run() {
            int total = 0;
            for (int round = 0; round < 200; round++) {
                int[] scratch = new int[4];
                scratch[0] = FACTOR.get()[0];
                total = total + scratch[0];
            }
            RESULTS[slot] = total;
        }
    }

    static final class Body implements Runnable {
        @Override
        public void run() {
            try (var scope = StructuredTaskScope.open()) {
                scope.fork(new Multiplier(0));
                scope.fork(new Multiplier(1));
                scope.join();
            } catch (InterruptedException | ExecutionException failure) {
                Serial.println("failed");
            }
        }
    }

    public static void main() throws Exception {
        ScopedValue.where(FACTOR, new int[] {7}).run(new Body());
        Serial.println(RESULTS[0]);
        Serial.println(RESULTS[1]);
    }
}
