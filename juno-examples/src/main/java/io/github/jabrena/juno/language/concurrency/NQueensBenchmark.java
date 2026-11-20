package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * Times one CPU-bound problem, counting the solutions of the N-Queens puzzle ({@link NQueens}), solved three ways on
 * the Nano ESP32's two cores: as one monolithic loop, split over two virtual threads, and split over two subtasks of a
 * {@link StructuredTaskScope}.
 *
 * <p>Each split hands a worker every other column for the first row's queen. The board is mirror-symmetric and, on a
 * board of even size, column {@code c} and its mirror {@code n - 1 - c} have opposite parity, so both workers get the
 * same amount of search. Nothing is allocated apart from the workers themselves.
 *
 * <p>Every mode prints its solution count beside the known one, so a timing is only trusted when the work was done.
 * Each size is warmed up once and then run {@value #RUNS} times; the board prints the best and the average time in
 * microseconds, and the speed-up of each parallel mode over the monolith's best time. Times include creating and
 * joining the workers, which dominates the smallest board. Build with {@code -Djuno.carriers=1} to run the same
 * program on one core and see the overhead without the parallelism.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NQueensBenchmark {
    /** The three ways the same search is run, in the order they are timed; the monolith comes first. */
    private enum Mode {
        MONOLITH,
        THREADS,
        SCOPE;

        // The board's enums carry only int-like fields and have no name(), so the label is a method.
        String label() {
            return switch (this) {
                case MONOLITH -> "monolith";
                case THREADS -> "threads";
                case SCOPE -> "scope";
            };
        }
    }

    // values() copies the array on every call, so it is read once.
    private static final Mode[] MODES = Mode.values();
    // An array length must be a compile-time constant on the board, so this repeats the number of modes.
    private static final int MODE_COUNT = 3;

    private static final int RUNS = 5;
    private static final int[] SIZES = {6, 8, 10, 12};
    private static final int[] SOLUTIONS = {4, 92, 724, 14200};

    private final Clock clock;
    // A worker cannot return an int, so each one stores its partial count in its own slot.
    private final int[] partials = new int[2];
    private final int[] best = new int[MODE_COUNT];
    private final int[] average = new int[MODE_COUNT];
    private final int[] solved = new int[MODE_COUNT];

    private NQueensBenchmark(Clock clock) {
        this.clock = clock;
    }

    public static void main(String[] args) throws Exception {
        Serial.begin(BaudRate.BAUD_115200);
        NQueensBenchmark benchmark = new NQueensBenchmark(MCU.getClock());
        // The rounds repeat, each after a pause long enough for a serial monitor started after the upload to attach.
        for (int round = 1; ; round++) {
            Thread.sleep(10_000);
            Serial.println("N-Queens benchmark round " + round);
            for (int index = 0; index < SIZES.length; index++) {
                benchmark.measure(SIZES[index], SOLUTIONS[index]);
            }
        }
    }

    /**
     * Times every mode on an {@code n} by {@code n} board and prints each mode's line as soon as it is measured; the
     * monolith goes first, so its time is known for the speed-ups.
     */
    private void measure(int n, int expected) throws Exception {
        int monolith = Mode.MONOLITH.ordinal();
        for (int index = 0; index < MODES.length; index++) {
            Mode mode = MODES[index];
            int slot = mode.ordinal();
            time(mode, n);
            String check = solved[slot] == expected ? " ok" : " WRONG, expected " + expected;
            String speedUp = mode == Mode.MONOLITH ? "" : " speed-up=" + hundredths(best[monolith] * 100 / best[slot]) + "x";
            Serial.println("N=" + n + " " + mode.label() + " best=" + best[slot] + "us avg=" + average[slot]
                    + "us solutions=" + solved[slot] + check + speedUp);
        }
    }

    /** Warms {@code mode} up once, then records its best and average time over {@value #RUNS} timed runs. */
    private void time(Mode mode, int n) throws Exception {
        int slot = mode.ordinal();
        solved[slot] = run(mode, n);
        int fastest = Integer.MAX_VALUE;
        int total = 0;
        for (int index = 0; index < RUNS; index++) {
            int start = clock.micros();
            int solutions = run(mode, n);
            // Subtracting ints stays correct when micros() wraps.
            int elapsed = clock.micros() - start;
            if (solutions != solved[slot]) {
                solved[slot] = -1;
            }
            fastest = Math.min(fastest, elapsed);
            total += elapsed;
        }
        best[slot] = Math.max(fastest, 1);
        average[slot] = total / RUNS;
    }

    /** Counts the solutions on an {@code n} by {@code n} board the way {@code mode} names. */
    private int run(Mode mode, int n) throws Exception {
        return switch (mode) {
            case MONOLITH -> NQueens.count(n, 0, 1);
            case THREADS -> runOnThreads(n);
            case SCOPE -> runInScope(n);
        };
    }

    /** Splits the search over two virtual threads and joins both. */
    private int runOnThreads(int n) throws InterruptedException {
        Thread even = Thread.startVirtualThread(() -> searchHalf(0, n));
        Thread odd = Thread.startVirtualThread(() -> searchHalf(1, n));
        even.join();
        odd.join();
        return partials[0] + partials[1];
    }

    /** Splits the search over two subtasks of a structured scope, which joins both before it closes. */
    private int runInScope(int n) throws InterruptedException, ExecutionException {
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> searchHalf(0, n));
            scope.fork(() -> searchHalf(1, n));
            scope.join();
        }
        return partials[0] + partials[1];
    }

    /** Counts the solutions whose first-row queen stands in a column of parity {@code half}, into its own slot. */
    private void searchHalf(int half, int n) {
        partials[half] = NQueens.count(n, half, 2);
    }

    /** Formats a value in hundredths, such as 187 as {@code 1.87}. */
    private static String hundredths(int value) {
        int fraction = value % 100;
        return (value / 100) + (fraction < 10 ? ".0" : ".") + fraction;
    }
}
