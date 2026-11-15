package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.util.concurrent.StructuredTaskScope;

/**
 * Soak test for {@link ScopedValue} bindings on the Portenta H7's Mbed RTOS thread port: every round binds, reads
 * and unbinds the same two scoped values again, so a carrier the port forgets to pop, a binding a forked subtask
 * never sees, or a chain that leaks into the next round shows up as a failure count that starts climbing.
 *
 * <p>A binding lives on the thread that made it, and the port has to carry it across a handover and into every
 * subtask of a scope. Each round therefore checks four things in turn:
 *
 * <ol>
 *   <li><b>inheritance</b>: one binding of {@code FACTOR} wraps a {@link StructuredTaskScope} whose three subtasks
 *       each multiply the bound factor by their own slot weight, so the total after {@code join} must be exactly
 *       42 — a subtask that saw no binding at all would have failed on {@code get()} instead;</li>
 *   <li><b>nested rebinding</b>: inside the outer binding, an inner one rebinds {@code FACTOR} to a second value,
 *       and the outer value must be visible again once the inner carrier's {@code run} has returned;</li>
 *   <li><b>two keys at once</b>: a carrier chain binding both {@code FACTOR} and {@code OFFSET} runs through
 *       {@code Carrier.call}, which must return the array the operation built from both bindings;</li>
 *   <li><b>unbinding</b>: back on {@code main} with every carrier closed, {@code isBound()} must be false for both
 *       keys and {@code orElse} must fall back to its default.</li>
 * </ol>
 *
 * <p>{@code inherit-failures}, {@code nested-failures}, {@code call-failures} and {@code unbound-failures} must all
 * stay at 0 for every round. The bound values are {@code static final} arrays that are only read, but binding one
 * passes it on rather than indexing it, so the compiler keeps all four in the arena ({@code JUNO-RISK-012}); they,
 * the subtask bodies and the readers are allocated once, before the first round. Each round allocates the
 * carriers, the scope and its subtask handles (the {@code JUNO-RISK-001} finding this program expects), the garbage
 * collector reclaims them as the bindings and the scope close, and the printed {@code arena} value must therefore
 * rise and fall around a stable level instead of climbing — a carrier chain that never unwinds is exactly what that
 * figure would reveal.
 *
 * <p>Flash it and watch it for a few minutes:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.serial.PortentaScopedValues \
 *     -Djuno.board=arduino-portenta-h7
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-portenta-h7 -Djuno.baudRate=115200
 * }</pre>
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaScopedValues {
    private static final int SLICES = 3;
    private static final int OUTER = 7;
    private static final int INNER = 11;
    private static final int OFFSET_AMOUNT = 100;
    private static final int MISSING = -1;
    /** {@code 7 * 1 + 7 * 2 + 7 * 3}: the bound factor, weighted by each subtask's slot. */
    private static final int EXPECTED_INHERITED = 6 * OUTER;

    private static final ScopedValue<int[]> FACTOR = ScopedValue.newInstance();
    private static final ScopedValue<int[]> OFFSET = ScopedValue.newInstance();

    // Bound values and the fallback: read-only tables, allocated once at startup (a bound array cannot stay in
    // flash, see JUNO-RISK-012).
    private static final int[] OUTER_FACTOR = {OUTER};
    private static final int[] INNER_FACTOR = {INNER};
    private static final int[] OFFSET_VALUE = {OFFSET_AMOUNT};
    private static final int[] FALLBACK = {MISSING};

    /** Reads the bound factor inside a subtask and weights it by its slot. */
    static final class WeightedReader implements Runnable {
        private final int[] counts;
        private final int slot;

        WeightedReader(int[] counts, int slot) {
            this.counts = counts;
            this.slot = slot;
        }

        @Override
        public void run() {
            counts[slot] = FACTOR.get()[0] * (slot + 1);
        }
    }

    /** Opens the scope that reads the binding its owner made: the body of the outer carrier's {@code run}. */
    static final class ScopeOpener implements Runnable {
        private final PortentaScopedValues owner;

        ScopeOpener(PortentaScopedValues owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            owner.forkReaders();
        }
    }

    /** Reads the outer binding, rebinds it, and reads it again after the inner carrier has returned. */
    static final class RebindingReader implements Runnable {
        private final PortentaScopedValues owner;

        RebindingReader(PortentaScopedValues owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            int before = FACTOR.get()[0];
            ScopedValue.where(FACTOR, INNER_FACTOR).run(owner.innerReader);
            int after = FACTOR.get()[0];
            if (before != OUTER || owner.innerSeen != INNER || after != OUTER) {
                owner.nestedFailures = owner.nestedFailures + 1;
            }
        }
    }

    /** Records the value the innermost binding makes visible. */
    static final class InnerReader implements Runnable {
        private final PortentaScopedValues owner;

        InnerReader(PortentaScopedValues owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            owner.innerSeen = FACTOR.get()[0];
        }
    }

    private final int[] counts = new int[SLICES];
    private final int[] combined = new int[2];
    private final WeightedReader firstReader = new WeightedReader(counts, 0);
    private final WeightedReader secondReader = new WeightedReader(counts, 1);
    private final WeightedReader thirdReader = new WeightedReader(counts, 2);
    private final ScopeOpener scopeOpener = new ScopeOpener(this);
    private final RebindingReader rebindingReader = new RebindingReader(this);
    private final InnerReader innerReader = new InnerReader(this);
    private int innerSeen;
    private int inheritFailures;
    private int nestedFailures;
    private int callFailures;
    private int unboundFailures;

    /**
     * Opens the serial port and repeats the four binding checks forever, printing the running totals every round.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Clock clock = MCU.getClock();
        BoardMemory memory = MCU.getBoardMemory();
        PortentaScopedValues example = new PortentaScopedValues();

        Serial.print("inherited expected ");
        Serial.println(EXPECTED_INHERITED);
        Serial.print("arena capacity ");
        Serial.println(memory.arenaCapacityBytes());

        int round = 0;
        while (true) {
            Serial.print("round ");
            Serial.println(round);
            int startedAt = clock.millis();
            example.runRound();
            Serial.print("inherited ");
            Serial.println(example.total());
            Serial.print("millis ");
            Serial.println(clock.millis() - startedAt);
            Serial.print("inherit-failures ");
            Serial.println(example.inheritFailures);
            Serial.print("nested-failures ");
            Serial.println(example.nestedFailures);
            Serial.print("call-failures ");
            Serial.println(example.callFailures);
            Serial.print("unbound-failures ");
            Serial.println(example.unboundFailures);
            Serial.print("arena ");
            Serial.println(memory.arenaUsedBytes());
            round = round + 1;
        }
    }

    /** One round: inheritance into a scope, nested rebinding, two keys through {@code call}, then unbinding. */
    private void runRound() {
        inheritIntoSubtasks();
        ScopedValue.where(FACTOR, OUTER_FACTOR).run(rebindingReader);
        callWithTwoKeys();
        checkUnbound();
    }

    /** The binding {@code main} makes must reach every subtask the scope forks inside it. */
    private void inheritIntoSubtasks() {
        counts[0] = 0;
        counts[1] = 0;
        counts[2] = 0;
        ScopedValue.where(FACTOR, OUTER_FACTOR).run(scopeOpener);
        if (total() != EXPECTED_INHERITED) {
            inheritFailures = inheritFailures + 1;
        }
    }

    /**
     * Runs the three readers in one scope. A {@code Runnable} cannot throw, so the join's failures are counted here
     * rather than propagated: either way the round's total comes out wrong and {@code inherit-failures} moves.
     */
    private void forkReaders() {
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(firstReader);
            scope.fork(secondReader);
            scope.fork(thirdReader);
            scope.join();
        } catch (Exception failure) {
            inheritFailures = inheritFailures + 1;
        }
    }

    /** A two-link carrier chain through {@code Carrier.call}, whose operation reads both bindings. */
    private void callWithTwoKeys() {
        combined[0] = 0;
        combined[1] = 0;
        int[] result = ScopedValue.where(FACTOR, OUTER_FACTOR)
                .where(OFFSET, OFFSET_VALUE)
                .call(() -> combine());
        if (result != combined || combined[0] != OUTER || combined[1] != OFFSET_AMOUNT) {
            callFailures = callFailures + 1;
        }
    }

    /** The body of {@code Carrier.call}: reads both bindings into the owner's buffer and returns it. */
    private int[] combine() {
        combined[0] = FACTOR.get()[0];
        combined[1] = OFFSET.get()[0];
        return combined;
    }

    /** Back on {@code main} with every carrier closed, neither key may still be bound. */
    private void checkUnbound() {
        if (FACTOR.isBound() || OFFSET.isBound() || FACTOR.orElse(FALLBACK)[0] != MISSING) {
            unboundFailures = unboundFailures + 1;
        }
    }

    private int total() {
        return counts[0] + counts[1] + counts[2];
    }
}
