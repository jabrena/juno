package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class StructuredTasks {
    private StructuredTasks() {
    }

    public static void main() throws Exception {
        StructuredTaskScope.Subtask<String> completed = null;
        StructuredTaskScope.Subtask<String> failed = null;
        try (var scope = StructuredTaskScope.<String>open()) {
            completed = scope.fork(() -> "completed");
            Callable<String> failing = () -> {
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
            // Either fork may finish first on a real JVM (threads race); the compiled runtime always runs
            // "winner" first, so only check that the result is one of the two valid answers.
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
