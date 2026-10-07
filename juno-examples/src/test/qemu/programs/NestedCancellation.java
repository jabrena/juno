package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.Serial;
import java.util.concurrent.StructuredTaskScope;

@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class NestedCancellation {
    private static final class Guard {
    }

    private static final Guard LOCK = new Guard();

    private NestedCancellation() {
    }

    public static void main() throws Exception {
        // The slow task holds LOCK and owns an inner scope when the fast sibling wins and cancels it.
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>anySuccessfulResultOrThrow())) {
            scope.fork(() -> {
                synchronized (LOCK) {
                    try (var inner = StructuredTaskScope.open()) {
                        inner.fork(() -> {
                            Thread.sleep(5000);
                            return "inner";
                        });
                        inner.join();
                    }
                }
                return "slow";
            });
            scope.fork(() -> {
                Thread.sleep(100);
                return "fast";
            });
            Serial.println(scope.join());
        }
        // The cancelled task's monitor must be free again.
        synchronized (LOCK) {
            Serial.println("lock free");
        }
        // The cancelled task and its inner subtask must have given their slots back: three more fit.
        final int[] done = {0, 0, 0};
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> { done[0] = 1; });
            scope.fork(() -> { done[1] = 10; });
            scope.fork(() -> { done[2] = 100; });
            scope.join();
        }
        Serial.println(done[0] + done[1] + done[2]);
    }
}
