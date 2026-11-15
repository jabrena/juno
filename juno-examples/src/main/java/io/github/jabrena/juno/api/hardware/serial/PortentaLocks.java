package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Soak test for mutual exclusion on the Portenta H7's Mbed RTOS thread port: every round runs the same two
 * contended counters again, so a lost update, a lock the scheduler never hands over, or an unlock the port loses
 * shows up as a failure count that starts climbing instead of as a single clean run.
 *
 * <p>Each round has three phases, each with two virtual threads (two workers plus {@code main} stay inside the
 * scheduler's four slots, as in {@link SerialThreads}):
 *
 * <ol>
 *   <li>two threads increment {@code guarded} 4000 times each inside {@code synchronized (this)}; the total must be
 *       exactly 8000;</li>
 *   <li>two threads increment {@code locked} the same number of times under a {@link ReentrantLock}, each
 *       increment taking the lock a second time in a nested method to exercise reentrancy, after which {@code main}
 *       adds one more increment through the zero-argument {@code tryLock}; the total must be exactly 8001;</li>
 *   <li>the same lock workers also increment {@code unguarded} outside the lock, and {@code lost} reports how many
 *       of those 8000 increments were dropped. This counter is a diagnostic, never a failure: a handover has to land
 *       in the two-instruction window between the load and the store to lose one, which is why {@code lost 0} is the
 *       usual result on this port. A non-zero value is real evidence of an unsynchronized read-modify-write losing
 *       an update, and the contrast with the two guarded totals above is the point.</li>
 * </ol>
 *
 * <p>{@code guarded-failures}, {@code locked-failures} and {@code trylock-failures} must stay at 0 for every round.
 * The lock, the thread bodies and the counters are
 * allocated once, before the first round; only the two {@code Thread} handles per phase and their Mbed stacks are
 * allocated per round (the {@code JUNO-RISK-001} finding this program expects), and the printed {@code arena} value
 * must rise and fall around a stable level rather than climb.
 *
 * <p>Flash it and watch it for a few minutes:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.serial.PortentaLocks \
 *     -Djuno.board=arduino-portenta-h7
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-portenta-h7 -Djuno.baudRate=115200
 * }</pre>
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaLocks {
    private static final int INCREMENTS = 4000;
    private static final int EXPECTED_GUARDED = 2 * INCREMENTS;
    private static final int EXPECTED_LOCKED = 2 * INCREMENTS + 1;

    /** Increments the shared counter under the owner's monitor. */
    static final class SynchronizedIncrementer implements Runnable {
        private final PortentaLocks owner;

        SynchronizedIncrementer(PortentaLocks owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            for (int index = 0; index < INCREMENTS; index++) {
                synchronized (owner) {
                    owner.guarded = owner.guarded + 1;
                }
            }
        }
    }

    /** Increments the shared counter under the owner's lock, and a second one with no lock at all. */
    static final class LockedIncrementer implements Runnable {
        private final PortentaLocks owner;

        LockedIncrementer(PortentaLocks owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            for (int index = 0; index < INCREMENTS; index++) {
                owner.lock.lock();
                try {
                    owner.incrementWhileHeld();
                } finally {
                    owner.lock.unlock();
                }
                owner.unguarded = owner.unguarded + 1;
            }
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final SynchronizedIncrementer firstMonitorWorker = new SynchronizedIncrementer(this);
    private final SynchronizedIncrementer secondMonitorWorker = new SynchronizedIncrementer(this);
    private final LockedIncrementer firstLockWorker = new LockedIncrementer(this);
    private final LockedIncrementer secondLockWorker = new LockedIncrementer(this);
    private int guarded;
    private int locked;
    private int unguarded;
    private int guardedFailures;
    private int lockedFailures;
    private int tryLockFailures;

    /**
     * Opens the serial port and repeats the contended counters forever, printing the running totals every round.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     * @throws InterruptedException never on the board; {@code join} declares it
     */
    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Clock clock = MCU.getClock();
        BoardMemory memory = MCU.getBoardMemory();
        PortentaLocks example = new PortentaLocks();

        Serial.print("guarded expected ");
        Serial.println(EXPECTED_GUARDED);
        Serial.print("locked expected ");
        Serial.println(EXPECTED_LOCKED);

        int round = 0;
        while (true) {
            Serial.print("round ");
            Serial.println(round);
            int startedAt = clock.millis();
            example.runRound();
            Serial.print("guarded ");
            Serial.println(example.guarded);
            Serial.print("locked ");
            Serial.println(example.locked);
            Serial.print("unguarded ");
            Serial.println(example.unguarded);
            Serial.print("lost ");
            Serial.println(EXPECTED_GUARDED - example.unguarded);
            Serial.print("millis ");
            Serial.println(clock.millis() - startedAt);
            Serial.print("guarded-failures ");
            Serial.println(example.guardedFailures);
            Serial.print("locked-failures ");
            Serial.println(example.lockedFailures);
            Serial.print("trylock-failures ");
            Serial.println(example.tryLockFailures);
            Serial.print("arena ");
            Serial.println(memory.arenaUsedBytes());
            round = round + 1;
        }
    }

    /** One round: the monitor pair, then the lock pair, then {@code main}'s own {@code tryLock} increment. */
    private void runRound() throws InterruptedException {
        guarded = 0;
        locked = 0;
        unguarded = 0;

        Thread firstMonitor = Thread.startVirtualThread(firstMonitorWorker);
        Thread secondMonitor = Thread.startVirtualThread(secondMonitorWorker);
        firstMonitor.join();
        secondMonitor.join();
        if (guarded != EXPECTED_GUARDED) {
            guardedFailures = guardedFailures + 1;
        }

        Thread firstLocked = Thread.startVirtualThread(firstLockWorker);
        Thread secondLocked = Thread.startVirtualThread(secondLockWorker);
        firstLocked.join();
        secondLocked.join();

        if (lock.tryLock()) {
            try {
                locked = locked + 1;
            } finally {
                lock.unlock();
            }
        } else {
            tryLockFailures = tryLockFailures + 1;
        }
        if (locked != EXPECTED_LOCKED) {
            lockedFailures = lockedFailures + 1;
        }
    }

    /** Takes the lock a second time: a reentrant acquisition the same thread already holds. */
    private void incrementWhileHeld() {
        lock.lock();
        try {
            locked = locked + 1;
        } finally {
            lock.unlock();
        }
    }
}
