package demo;

import io.github.jabrena.juno.api.io.serial.Serial;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.locks.ReentrantLock;

/** Forked subtasks on the cooperative scheduler, with a lock, a scoped value and a sleep: each board's thread port. */
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
