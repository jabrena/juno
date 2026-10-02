package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.Serial;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

@Board(ArduinoUnoR4WiFi.class)
public final class StructuredTasks {
    private static int cancelledRuns;

    private StructuredTasks() {
    }

    public static void main() throws Exception {
        StructuredTaskScope.Subtask<?> completed = null;
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            completed = scope.fork(() -> "completed");
            Callable<String> failing = () -> {
                throw new IllegalStateException("boom");
            };
            scope.fork(failing);
            scope.fork(() -> {
                cancelledRuns = cancelledRuns + 1;
                return "too late";
            });
            scope.join().throwIfFailed();
        } catch (ExecutionException failure) {
            Serial.println(failure.getMessage());
        }
        Serial.println(completed.get() == "completed" ? 1 : 0);
        Serial.println(cancelledRuns);

        cancelledRuns = 0;
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<Object>()) {
            scope.fork(() -> "winner");
            scope.fork(() -> {
                cancelledRuns = cancelledRuns + 1;
                return "too late";
            });
            scope.join();
            Serial.println(scope.result() == "winner" ? 1 : 0);
        }
        Serial.println(cancelledRuns);
    }
}
