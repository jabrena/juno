package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.Serial;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Every supported atomic operation, plus counters shared by subtasks that yield in the middle of their work. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Atomics {
    static final AtomicInteger SHARED = new AtomicInteger();
    static final AtomicLong WIDE_SHARED = new AtomicLong(1L << 32);

    static void bump(AtomicInteger counter) {
        for (int i = 0; i < 25; i++) {
            counter.incrementAndGet();
            WIDE_SHARED.addAndGet(3_000_000_000L);
            Thread.yield();
            SHARED.getAndAdd(2);
        }
    }

    public static void main() throws Exception {
        AtomicInteger number = new AtomicInteger(5);
        Serial.println(number.get());
        number.set(7);
        Serial.println(number.incrementAndGet());
        Serial.println(number.getAndIncrement());
        Serial.println(number.decrementAndGet());
        Serial.println(number.getAndDecrement());
        Serial.println(number.addAndGet(10));
        Serial.println(number.getAndAdd(-4));
        Serial.println(number.getAndSet(100));
        Serial.println(number.compareAndSet(100, 1) ? 1 : 0);
        Serial.println(number.compareAndSet(100, 2) ? 1 : 0);
        Serial.println(number.intValue());
        AtomicInteger overflow = new AtomicInteger(Integer.MAX_VALUE);
        Serial.println(overflow.incrementAndGet());
        Serial.println(new AtomicInteger().get());

        AtomicBoolean flag = new AtomicBoolean();
        Serial.println(flag.get() ? 1 : 0);
        flag.set(true);
        Serial.println(flag.get() ? 1 : 0);
        Serial.println(flag.getAndSet(false) ? 1 : 0);
        Serial.println(flag.compareAndSet(false, true) ? 1 : 0);
        Serial.println(flag.compareAndSet(false, true) ? 1 : 0);
        Serial.println(new AtomicBoolean(true).get() ? 1 : 0);

        AtomicLong wide = new AtomicLong(4_000_000_000L);
        Serial.println(wide.get());
        Serial.println(wide.incrementAndGet());
        Serial.println(wide.getAndIncrement());
        Serial.println(wide.decrementAndGet());
        Serial.println(wide.getAndDecrement());
        Serial.println(wide.addAndGet(-5_000_000_000L));
        Serial.println(wide.getAndAdd(9_000_000_000L));
        Serial.println(wide.getAndSet(-1L));
        Serial.println(wide.compareAndSet(-1L, 1L << 40) ? 1 : 0);
        Serial.println(wide.compareAndSet(-1L, 0L) ? 1 : 0);
        Serial.println(wide.longValue());
        Serial.println(new AtomicLong().get());
        wide.set(Long.MIN_VALUE);
        Serial.println(wide.decrementAndGet());

        AtomicInteger counter = new AtomicInteger();
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> bump(counter));
            scope.fork(() -> bump(counter));
            scope.fork(() -> bump(counter));
            scope.join();
        }
        Serial.println(counter.get());
        Serial.println(SHARED.get());
        Serial.println(WIDE_SHARED.get());
    }
}
