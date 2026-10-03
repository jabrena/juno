package io.github.jabrena.juno;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import java.util.concurrent.StructuredTaskScope;

/** JDK 25 preview structured concurrency lowered onto Juno's cooperative task runtime. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class StructuredTasks {
    private StructuredTasks() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<Object>anySuccessfulResultOrThrow())) {
            scope.fork(() -> "first result");
            scope.fork(() -> "cancelled sibling");
            Object result = scope.join();
            Serial.println(result == "first result" ? 1 : 0);
            Serial.println(scope.isCancelled() ? 1 : 0);
        }

        try (var scope = StructuredTaskScope.open()) {
            var runnable = scope.fork(() -> { Serial.println("runnable completed"); });
            scope.join();
            Serial.println(runnable.state() == StructuredTaskScope.Subtask.State.SUCCESS ? 1 : 0);
        }
    }
}
