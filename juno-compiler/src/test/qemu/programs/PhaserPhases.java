package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic Phaser behavior shared by OpenJDK and Juno. Three parties run three phases; one of the workers leaves in
 * the second phase with arriveAndDeregister, so the last phase is run by two parties. The phase reached, the parties
 * left and the number of arrivals do not depend on the order the threads arrive in. A second phaser checks that an
 * interrupted awaitAdvanceInterruptibly throws and leaves the phase where it was.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class PhaserPhases {
    static final Phaser PHASER = new Phaser(3);
    static final Phaser GATE = new Phaser(2);
    static final AtomicInteger ARRIVALS = new AtomicInteger();
    static volatile int gateOutcome;

    static void phased() {
        for (int i = 0; i < 3; i++) {
            ARRIVALS.incrementAndGet();
            PHASER.arriveAndAwaitAdvance();
        }
    }

    static void dropper() {
        ARRIVALS.incrementAndGet();
        PHASER.arriveAndAwaitAdvance();
        PHASER.arriveAndDeregister();
    }

    public static void main() throws Exception {
        Thread phasedWorker = Thread.startVirtualThread(PhaserPhases::phased);
        Thread dropperWorker = Thread.startVirtualThread(PhaserPhases::dropper);
        for (int i = 0; i < 3; i++) {
            ARRIVALS.incrementAndGet();
            PHASER.arriveAndAwaitAdvance();
        }
        phasedWorker.join();
        dropperWorker.join();
        Serial.println(PHASER.getPhase());
        Serial.println(PHASER.getRegisteredParties());
        Serial.println(PHASER.getUnarrivedParties());
        Serial.println(ARRIVALS.get());

        Thread gated = Thread.startVirtualThread(() -> {
            try {
                GATE.awaitAdvanceInterruptibly(GATE.getPhase());
                gateOutcome = 9;
            } catch (InterruptedException e) {
                gateOutcome = 1;
            }
        });
        gated.interrupt();
        gated.join();
        Serial.println(gateOutcome);
        Serial.println(GATE.getPhase());
    }
}
