package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.util.NoSuchElementException;
import java.util.concurrent.StructuredTaskScope;

@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class ScopedValues {
    private static final ScopedValue<String> USER = ScopedValue.newInstance();
    private static final ScopedValue<String> ROLE = ScopedValue.newInstance();
    private static final ScopedValue<int[]> SLOTS = ScopedValue.newInstance();

    private ScopedValues() {
    }

    public static void main() throws Exception {
        Serial.println(USER.isBound() ? 1 : 0);
        Serial.println(USER.orElse("nobody"));
        try {
            USER.get();
        } catch (NoSuchElementException unbound) {
            Serial.println("unbound");
        }

        ScopedValue.where(USER, "alice").where(ROLE, "admin").run(() -> {
            Serial.println(USER.get());
            Serial.println(ROLE.get());
            ScopedValue.where(USER, "nested").run(() -> {
                Serial.println(USER.get());
                Serial.println(ROLE.get());
            });
            Serial.println(USER.get());
        });
        Serial.println(USER.isBound() ? 1 : 0);

        String called = ScopedValue.where(USER, "bob").call(() -> USER.get());
        Serial.println(called);

        // An exception leaving a binding restores the outer bindings.
        try {
            ScopedValue.where(USER, "doomed").run(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException failure) {
            Serial.println(failure.getMessage());
        }
        Serial.println(USER.isBound() ? 1 : 0);

        // Subtasks inherit the owner's bindings and can rebind them for themselves.
        final int[] slots = new int[3];
        ScopedValue.where(USER, "owner").where(SLOTS, slots).run(() -> {
            try (var scope = StructuredTaskScope.open()) {
                var first = scope.fork(() -> {
                    SLOTS.get()[0] = 1;
                    return USER.get();
                });
                scope.fork(() -> {
                    SLOTS.get()[1] = 2;
                });
                var third = scope.fork(() -> ScopedValue.where(USER, "child").call(() -> {
                    SLOTS.get()[2] = 3;
                    return USER.get();
                }));
                scope.join();
                Serial.println(first.get());
                Serial.println(third.get());
                Serial.println(USER.get());
            } catch (InterruptedException | java.util.concurrent.ExecutionException interrupted) {
                Serial.println("interrupted");
            }
        });
        Serial.println(slots[0] + slots[1] + slots[2]);
    }
}
