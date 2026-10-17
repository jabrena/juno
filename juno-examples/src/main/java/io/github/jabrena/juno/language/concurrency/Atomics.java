package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The board-supported atomic-cell subset: {@link AtomicInteger}, {@link AtomicLong} and {@link AtomicBoolean}.
 *
 * <p>Two workers update the numeric cells without a lock. They then compete for a one-time Boolean claim with
 * {@code compareAndSet}; exactly one worker can win it. The owner uses {@code getAndSet} to reset and restore that
 * flag after the workers have joined. Every cell is allocated once before the workers start.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class Atomics {
    private static final int ROUNDS = 100;

    private final AtomicInteger integer = new AtomicInteger();
    private final AtomicLong longValue = new AtomicLong();
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final AtomicInteger winners = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        Atomics example = new Atomics();
        example.verify();
        Serial.println("AtomicInteger " + example.integer.get() + ", expected " + 2 * ROUNDS);
        Serial.println("AtomicLong " + example.longValue.get() + ", expected " + 4L * ROUNDS);
        Serial.println("AtomicBoolean winners " + example.winners.get() + ", flag " + example.claimed.get());
    }

    int verify() throws InterruptedException {
        Thread first = Thread.startVirtualThread(this::worker);
        Thread second = Thread.startVirtualThread(this::worker);
        first.join();
        second.join();

        boolean wasClaimed = claimed.getAndSet(false);
        if (!wasClaimed || winners.get() != 1) {
            throw new AssertionError("AtomicBoolean claim was not unique");
        }
        if (!claimed.compareAndSet(false, true)) {
            throw new AssertionError("AtomicBoolean compareAndSet failed");
        }
        return integer.get() + (int) longValue.get();
    }

    private void worker() {
        for (int round = 0; round < ROUNDS; round++) {
            integer.incrementAndGet();
            longValue.addAndGet(2L);
        }
        if (claimed.compareAndSet(false, true)) {
            winners.incrementAndGet();
        }
    }
}
