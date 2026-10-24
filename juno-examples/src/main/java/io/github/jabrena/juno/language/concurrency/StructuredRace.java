package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Two ways a {@link StructuredTaskScope} arbitrates a race between its subtasks. The scope's joiner decides the
 * outcome, and the scope cancels whatever is still
 * running once it has an answer.
 *
 * <p>The first round uses {@code anySuccessfulOrThrow}: the fast subtask sleeps 20 ms and the slow one 200 ms, so the
 * fast one wins and the slow one is cancelled before it returns. The second round uses {@code allSuccessfulOrThrow}:
 * one subtask fails, the join throws, and the long-running sibling is cancelled. The sleeps are long enough that the
 * outcomes come out the same on every run.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class})
public final class StructuredRace {
    private static final long FAST_MS = 20;
    private static final long SLOW_MS = 200;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        StructuredRace example = new StructuredRace();
        example.firstSuccessWins();
        example.oneFailureCancelsTheRest();
    }

    /** The first successful subtask is the winner, and the slower one is cancelled. */
    @SuppressWarnings("java:S2142")
    private void firstSuccessWins() throws InterruptedException {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>anySuccessfulOrThrow())) {
            scope.fork(() -> sleepThen(SLOW_MS, "slow"));
            scope.fork(() -> sleepThen(FAST_MS, "fast"));
            String winner = scope.join();
            Serial.println("winner " + winner);
            Serial.println("cancelled " + scope.isCancelled());
        } catch (Exception failure) {
            Serial.println("no winner");
        }
    }

    /** One failing subtask makes the join throw, and the subtask still running is cancelled. */
    @SuppressWarnings("java:S2142")
    private void oneFailureCancelsTheRest() throws InterruptedException {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {
            Subtask<String> slow = scope.fork(() -> sleepThen(SLOW_MS, "slow"));
            scope.fork(() -> failAfter(FAST_MS));
            scope.join();
            Serial.println("unexpected " + slow.get());
        } catch (Exception failure) {
            Serial.println("failed " + failure.getMessage());
        }
    }

    private static String sleepThen(long millis, String result) throws InterruptedException {
        Thread.sleep(millis);
        return result;
    }

    private static String failAfter(long millis) throws InterruptedException {
        Thread.sleep(millis);
        throw new IllegalStateException("sensor offline");
    }
}
