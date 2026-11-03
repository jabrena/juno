package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class})
public final class StructuredTasks {
    static volatile boolean completedDone;

    private StructuredTasks() {
    }

    public static void main() throws Exception {
        StructuredTaskScope.Subtask<String> completed = null;
        StructuredTaskScope.Subtask<String> failed = null;
        try (var scope = StructuredTaskScope.<String>open()) {
            completed = scope.fork(() -> {
                completedDone = true;
                return "completed";
            });
            // Subtasks are preemptive, as on the JVM: without this wait the failure could cancel "completed" before
            // it returns, leaving it UNAVAILABLE.
            Callable<String> failing = () -> {
                while (!completedDone) {
                    // Wait until the sibling has finished.
                }
                throw new IllegalStateException("boom");
            };
            failed = scope.fork(failing);
            scope.fork(() -> "possibly cancelled");
            try {
                scope.join();
            } catch (java.util.concurrent.ExecutionException failure) {
                Serial.println("failed");
                Serial.println(scope.isCancelled() ? 1 : 0);
            }
        }
        Serial.println(completed.state() == StructuredTaskScope.Subtask.State.SUCCESS ? 1 : 0);
        Serial.println(completed.get() == "completed" ? 1 : 0);
        Serial.println(failed.state() == StructuredTaskScope.Subtask.State.FAILED ? 1 : 0);
        Serial.println(failed.exception().getMessage());

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<Object>anySuccessfulOrThrow())) {
            scope.fork(() -> "winner");
            scope.fork(() -> "possibly cancelled");
            Object winner = scope.join();
            // Either fork may finish first (subtasks race, on the JVM and on the board), so only check that the
            // result is one of the two valid answers.
            Serial.println(winner == "winner" || winner == "possibly cancelled" ? 1 : 0);
            Serial.println(scope.isCancelled() ? 1 : 0);
        }

        final int[] ran = {0};
        try (var scope = StructuredTaskScope.open()) {
            var runnable = scope.fork(() -> { ran[0] = 1; });
            scope.join();
            Serial.println(runnable.state() == StructuredTaskScope.Subtask.State.SUCCESS ? 1 : 0);
        }
        Serial.println(ran[0]);
    }
}
