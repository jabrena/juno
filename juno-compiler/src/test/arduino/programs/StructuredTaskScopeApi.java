package demo;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.locks.ReentrantLock;

/** Forked cooperative subtasks, with a lock, a scoped value and a sleep: each board's thread port. */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class, ArduinoOneR3WiFiRev2Board.class})
public final class StructuredTaskScopeApi {
    private static final ScopedValue<String> NAME = ScopedValue.newInstance();
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final int[] COUNT = new int[1];

    static void work() {
        LOCK.lock();
        try {
            COUNT[0] = COUNT[0] + 1;
        } finally {
            LOCK.unlock();
        }
        try {
            Thread.sleep(5);
        } catch (InterruptedException interrupted) {
            Serial.println("interrupted");
        }
        Serial.println(NAME.get());
    }

    static void forkAll() {
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> work());
            scope.fork(() -> work());
            scope.fork(() -> work());
            scope.join();
        } catch (Exception failure) {
            Serial.println("failed");
        }
    }

    public static void main(String[] args) {
        ScopedValue.where(NAME, "task").run(() -> forkAll());
        Serial.println(COUNT[0]);
    }
}
