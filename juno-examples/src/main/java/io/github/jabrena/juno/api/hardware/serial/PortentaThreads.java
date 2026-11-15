package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * Soak test for the Portenta H7's Mbed RTOS thread port: the same three virtual threads are started and joined again
 * in every round, for as long as the board runs, so a scheduler hang, a leak in the port's per-thread blocks or a
 * lost result shows up as a stalled round counter or a growing failure count rather than as a one-off success.
 *
 * <p>{@link SerialThreads} exercises threads, locks, structured concurrency and scoped values once at startup and
 * then only reports; this program instead repeats one of those checks under load. Each round:
 *
 * <ol>
 *   <li>clears the result slots and starts three virtual threads, one per slice of {@code [2, 3000)};</li>
 *   <li>spins on {@code main} while they run — {@code main} never sleeps or yields, so it keeps running only because
 *       the backend's safepoints preempt it (the Mbed port has no asynchronous tick), and the spin count is the
 *       evidence that it kept making progress alongside the workers;</li>
 *   <li>joins all three, checks that none is still alive afterwards, and compares the total against the reference
 *       count that {@code main} computed serially at startup.</li>
 * </ol>
 *
 * <p>Every mismatch is counted rather than printed once, so a monitor attached late still sees the running totals:
 * {@code mismatches} and {@code alive-after-join} must both stay at 0 for every round. The arena figure is the
 * leak check for the port itself. Each round allocates three {@code Thread} handles and their Mbed stacks (the
 * {@code JUNO-RISK-001} finding this program expects), the garbage collector reclaims them once the threads exit,
 * and the printed {@code arena} value must therefore rise and fall around a stable level instead of climbing. The
 * thread bodies and the result slots are allocated once, before the first round.
 *
 * <p>Flash it and watch it for a few minutes:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.serial.PortentaThreads \
 *     -Djuno.board=arduino-portenta-h7
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-portenta-h7 -Djuno.baudRate=115200
 * }</pre>
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaThreads {
    private static final int WORKERS = 3;
    private static final int LIMIT = 3000;
    private static final int SLICE = LIMIT / WORKERS;

    /** Counts the primes in {@code [from, to)} by trial division into {@code counts[slot]}. */
    static final class PrimeCounter implements Runnable {
        private final int[] counts;
        private final int slot;
        private final int from;
        private final int to;

        PrimeCounter(int[] counts, int slot, int from, int to) {
            this.counts = counts;
            this.slot = slot;
            this.from = from;
            this.to = to;
        }

        @Override
        public void run() {
            counts[slot] = primesIn(from, to);
        }
    }

    private final int[] counts = new int[WORKERS];
    private final PrimeCounter firstWorker = new PrimeCounter(counts, 0, 2, SLICE);
    private final PrimeCounter secondWorker = new PrimeCounter(counts, 1, SLICE, 2 * SLICE);
    private final PrimeCounter thirdWorker = new PrimeCounter(counts, 2, 2 * SLICE, LIMIT);
    private long spins;
    private int mismatches;
    private int aliveAfterJoin;

    /**
     * Opens the serial port, computes the reference count serially, and then repeats the threaded count forever.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     * @throws InterruptedException never on the board; {@code join} declares it
     */
    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Clock clock = MCU.getClock();
        BoardMemory memory = MCU.getBoardMemory();
        PortentaThreads example = new PortentaThreads();
        int expected = primesIn(2, LIMIT);

        Serial.print("primes expected ");
        Serial.println(expected);
        Serial.print("arena capacity ");
        Serial.println(memory.arenaCapacityBytes());

        int round = 0;
        while (true) {
            Serial.print("round ");
            Serial.println(round);
            int startedAt = clock.millis();
            example.runRound(expected);
            Serial.print("primes ");
            Serial.println(example.total());
            Serial.print("spins ");
            Serial.println(example.spins);
            Serial.print("millis ");
            Serial.println(clock.millis() - startedAt);
            Serial.print("mismatches ");
            Serial.println(example.mismatches);
            Serial.print("alive-after-join ");
            Serial.println(example.aliveAfterJoin);
            Serial.print("arena ");
            Serial.println(memory.arenaUsedBytes());
            round = round + 1;
        }
    }

    /** One round: start the three workers, keep {@code main} busy while they run, then join and check the result. */
    private void runRound(int expected) throws InterruptedException {
        counts[0] = 0;
        counts[1] = 0;
        counts[2] = 0;
        spins = 0;

        Thread first = Thread.startVirtualThread(firstWorker);
        Thread second = Thread.startVirtualThread(secondWorker);
        Thread third = Thread.startVirtualThread(thirdWorker);
        while (first.isAlive() || second.isAlive() || third.isAlive()) {
            spins = spins + 1;
        }
        first.join();
        second.join();
        third.join();

        if (first.isAlive() || second.isAlive() || third.isAlive()) {
            aliveAfterJoin = aliveAfterJoin + 1;
        }
        if (total() != expected) {
            mismatches = mismatches + 1;
        }
    }

    private int total() {
        return counts[0] + counts[1] + counts[2];
    }

    private static int primesIn(int from, int to) {
        int found = 0;
        for (int candidate = from; candidate < to; candidate++) {
            if (isPrime(candidate)) {
                found++;
            }
        }
        return found;
    }

    private static boolean isPrime(int value) {
        if (value < 2) {
            return false;
        }
        for (int divisor = 2; divisor * divisor <= value; divisor++) {
            if (value % divisor == 0) {
                return false;
            }
        }
        return true;
    }
}
