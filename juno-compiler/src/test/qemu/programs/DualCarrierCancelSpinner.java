package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

/**
 * A failing subtask cancels a sibling that is busy spinning, possibly on the other carrier at that moment. Its task
 * cannot be deleted while it runs there, so it has to stop itself; the scope must still close and the program go on.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class DualCarrierCancelSpinner {
    private static volatile boolean stop;
    private static volatile int progress;

    private static void spin() {
        while (!stop) {
            progress++;
        }
    }

    private static boolean failureCancelsBusySibling() throws InterruptedException {
        stop = false;
        progress = 0;
        boolean failed = false;
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(DualCarrierCancelSpinner::spin);
            Callable<String> failing = () -> {
                while (progress == 0) {
                    // Wait until the sibling is demonstrably running.
                }
                throw new IllegalStateException("sensor offline");
            };
            scope.fork(failing);
            try {
                scope.join();
            } catch (Exception failure) {
                failed = true;
            }
            // The board has already cancelled the spinner; on the JVM, cancellation only interrupts it.
            stop = true;
        }
        return failed;
    }

    public static void main() throws Exception {
        for (int round = 0; round < 3; round++) {
            Serial.println(failureCancelsBusySibling() ? 1 : 0);
        }
        Serial.println("closed");
    }
}
