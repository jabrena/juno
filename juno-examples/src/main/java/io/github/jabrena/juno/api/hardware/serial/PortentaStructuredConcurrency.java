package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Soak test for {@link StructuredTaskScope} on the Portenta H7's Mbed RTOS thread port: the same three scopes are
 * opened, forked, joined and closed again in every round, including one round-trip through the cancellation path,
 * which is where the port terminates an Mbed thread that is still running.
 *
 * <p>Each round opens three scopes in turn, each with its own joiner and at most three subtasks (so the subtasks
 * plus {@code main} stay inside the scheduler's four slots, as in {@link SerialThreads}):
 *
 * <ol>
 *   <li>the default {@code open()}: three {@code Runnable} subtasks count the primes in one slice of
 *       {@code [2, 3000)} each, and the total after {@code join} must equal the reference count that {@code main}
 *       computed serially at startup;</li>
 *   <li>{@code open(Joiner.awaitAllSuccessfulOrThrow())}: the same three slices as {@code Callable} subtasks, so the
 *       round also checks each {@link Subtask}'s {@code state()} and {@code get()} once the scope has joined;</li>
 *   <li>{@code open(Joiner.awaitAllSuccessfulOrThrow())} again, this time with one subtask that fails after 20 ms
 *       and one that would sleep for 200 ms: {@code join} must throw, and {@code cancel-millis} says whether the
 *       sleeper was really cancelled (about 20-40 ms) or ran to its end (about 200 ms).</li>
 * </ol>
 *
 * <p>{@code runnable-failures}, {@code callable-failures} and {@code cancel-failures} must all stay at 0 for every
 * round, and both time figures must stay stable: a lost handover shows up as a round counter that stops advancing,
 * and a cancellation the port delivers only sometimes as a {@code cancel-millis} that jumps between 40 and 200. Each round allocates
 * three scopes, their subtask handles and the Mbed stacks behind them (the {@code JUNO-RISK-001} finding this
 * program expects); the garbage collector reclaims them as the scopes close, so the printed {@code arena} value
 * must rise and fall around a stable level rather than climb. The subtask bodies and the result slots are allocated
 * once, before the first round.
 *
 * <p>Flash it and watch it for a few minutes:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.serial.PortentaStructuredConcurrency \
 *     -Djuno.board=arduino-portenta-h7
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-portenta-h7 -Djuno.baudRate=115200
 * }</pre>
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaStructuredConcurrency {
    private static final int SLICES = 3;
    private static final int LIMIT = 3000;
    private static final int SLICE = LIMIT / SLICES;
    private static final long FAST_MILLIS = 20;
    private static final long SLOW_MILLIS = 200;

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

    private final int[] counts = new int[SLICES];
    private final PrimeCounter firstCounter = new PrimeCounter(counts, 0, 2, SLICE);
    private final PrimeCounter secondCounter = new PrimeCounter(counts, 1, SLICE, 2 * SLICE);
    private final PrimeCounter thirdCounter = new PrimeCounter(counts, 2, 2 * SLICE, LIMIT);
    private int cancelMillis;
    private int runnableFailures;
    private int callableFailures;
    private int cancelFailures;

    /**
     * Opens the serial port, computes the reference count serially, and then repeats the three scopes forever.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     * @throws InterruptedException never on the board; {@code join} declares it
     * @throws ExecutionException never in the first two scopes; the third one catches its own failure
     */
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Clock clock = MCU.getClock();
        BoardMemory memory = MCU.getBoardMemory();
        PortentaStructuredConcurrency example = new PortentaStructuredConcurrency();
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
            example.forkRunnables(expected);
            example.forkCallables(expected);
            example.cancelOnFailure(clock);
            Serial.print("primes ");
            Serial.println(example.total());
            Serial.print("millis ");
            Serial.println(clock.millis() - startedAt);
            Serial.print("cancel-millis ");
            Serial.println(example.cancelMillis);
            Serial.print("runnable-failures ");
            Serial.println(example.runnableFailures);
            Serial.print("callable-failures ");
            Serial.println(example.callableFailures);
            Serial.print("cancel-failures ");
            Serial.println(example.cancelFailures);
            Serial.print("arena ");
            Serial.println(memory.arenaUsedBytes());
            round = round + 1;
        }
    }

    /** The default scope with three {@code Runnable} subtasks: every fork must have run by the time join returns. */
    private void forkRunnables(int expected) throws InterruptedException, ExecutionException {
        clear();
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(firstCounter);
            scope.fork(secondCounter);
            scope.fork(thirdCounter);
            scope.join();
            if (scope.isCancelled()) {
                runnableFailures = runnableFailures + 1;
            }
        }
        if (total() != expected) {
            runnableFailures = runnableFailures + 1;
        }
    }

    /** The same work as {@code Callable} subtasks, so the round can also check each subtask's state and result. */
    private void forkCallables(int expected) throws InterruptedException, ExecutionException {
        clear();
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<int[]>awaitAllSuccessfulOrThrow())) {
            Subtask<int[]> first = scope.fork(() -> count(firstCounter));
            Subtask<int[]> second = scope.fork(() -> count(secondCounter));
            Subtask<int[]> third = scope.fork(() -> count(thirdCounter));
            scope.join();
            // Inlined rather than factored into a helper: a Subtask cannot be a method parameter on the board.
            boolean succeeded = first.state() == Subtask.State.SUCCESS && first.get() == counts
                    && second.state() == Subtask.State.SUCCESS && second.get() == counts
                    && third.state() == Subtask.State.SUCCESS && third.get() == counts;
            if (!succeeded) {
                callableFailures = callableFailures + 1;
            }
        }
        if (total() != expected) {
            callableFailures = callableFailures + 1;
        }
    }

    /**
     * One subtask fails, so the join must throw and the sleeping sibling is cancelled with the scope. A join that
     * returns normally is the failure; how long the phase took is reported rather than judged, because the scope's
     * {@code close} also waits for the cancelled sleeper: about 20-40 ms means it was cancelled, about 200 ms means
     * it ran to its end.
     */
    private void cancelOnFailure(Clock clock) throws InterruptedException {
        int startedAt = clock.millis();
        boolean threw = false;
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<int[]>awaitAllSuccessfulOrThrow())) {
            scope.fork(this::sleepThenReturn);
            scope.fork(this::failAfterSleeping);
            scope.join();
        } catch (Exception failure) {
            threw = true;
        }
        cancelMillis = clock.millis() - startedAt;
        if (!threw) {
            cancelFailures = cancelFailures + 1;
        }
    }

    /** Runs one counter inside a {@code Callable} subtask and hands the owner's slot array back as its result. */
    private int[] count(PrimeCounter counter) {
        counter.run();
        return counts;
    }

    /** Sleeps far longer than {@link #failAfterSleeping()} takes to fail: it finishes only if cancellation is lost. */
    private int[] sleepThenReturn() throws InterruptedException {
        Thread.sleep(SLOW_MILLIS);
        return counts;
    }

    /** Fails after a short delay, so the scope cancels whatever its sibling is still doing. */
    private int[] failAfterSleeping() throws InterruptedException {
        Thread.sleep(FAST_MILLIS);
        throw new IllegalStateException("subtask failed");
    }

    private void clear() {
        counts[0] = 0;
        counts[1] = 0;
        counts[2] = 0;
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
