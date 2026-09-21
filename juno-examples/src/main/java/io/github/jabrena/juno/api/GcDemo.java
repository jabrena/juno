package io.github.jabrena.juno.api;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * A minimal, dedicated example for watching Juno's conservative mark/sweep garbage collector, both
 * when it succeeds and when it can't help.
 *
 * <p><b>Ticks 0-999:</b> every tick allocates a small array, touches it, and lets it go out of
 * scope — nothing keeps it reachable past that point, so it becomes garbage the instant the next
 * tick starts. With an 8 KiB arena and ~404 bytes (400-byte payload + 4-byte header) allocated per
 * tick, the arena would exhaust in about 20 ticks without reclamation; instead, it runs for a full
 * 1000 ticks (~50 seconds), each collection reclaiming the previous tick's array.
 *
 * <p><b>Tick 1000 onward:</b> the program switches to keeping every subsequent allocation reachable
 * forever (stashed into one of a fixed set of named locals, {@code retained0}..{@code retained24},
 * that never get overwritten — Juno has no reference-typed array elements or dynamic collections, so
 * a fixed shift register of named locals, the same pattern {@code LedMatrixSnake} uses for its body,
 * is how a closed-world program keeps N things reachable at once). A conservative collector can
 * never reclaim something still reachable, so the arena genuinely fills up: 25 retained blocks
 * &times; ~404 bytes each is over 10 KB, comfortably past the 8 KiB arena, so within about 20 more
 * ticks (~1 second) it can no longer satisfy an allocation even after collecting, and
 * {@code juno_panic()} halts the program — demonstrating that the collector only helps when data
 * actually becomes unreachable; it never raises the ceiling on how much can be alive at once.
 *
 * <p><b>{@code @Watchdog}:</b> rather than hanging forever, this class also carries
 * {@code @Watchdog(timeoutMillis = 3000)}, so once {@code juno_panic()} stops kicking it (see
 * {@link Watchdog}), the RA4M1's hardware watchdog resets the board about 3 seconds later — printing
 * a {@code [juno-watchdog]} diagnostic first — and the whole cycle (ticks 0-999 reclaiming fine,
 * then exhaustion, then reboot) starts over from {@code tick 0} indefinitely.
 *
 * <p>Compile with {@code --gc-log} (CLI) or {@code -Djuno.gcLog=true} (Maven plugin) to see each
 * collection reclaim ticks 0-999's arrays, then see collections stop shrinking arena usage once
 * retention starts, e.g.:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *     -Djuno.main=io.github.jabrena.juno.api.GcDemo -Djuno.gcLog=true
 * ./mvnw -f juno-examples/pom.xml juno:monitor
 * }</pre>
 * which prints lines like {@code [juno-gc] collect: used 8080 -> 7676} for ticks 0-999, then lines
 * where "before" keeps climbing without ever coming back down, then the pre-panic
 * {@code [juno-gc] OOM: need <n> bytes, arena_used=<used>/<capacity>} diagnostic, then
 * {@code [juno-watchdog] panic: board will reset via watchdog in 3000ms}, then (about 3 seconds
 * later) {@code tick 0} again as the board comes back up. Without {@code --gc-log}, only the
 * {@code tick N} lines and the two unconditional pre-panic diagnostics (OOM and watchdog — see
 * {@code CortexM4AsmBackend}'s {@code juno_alloc}/{@code juno_panic}) appear.
 */
@Board(ArduinoUnoR4WiFi.class)
@Watchdog(timeoutMillis = 3000)
public final class GcDemo {
    private static final int INTS_PER_TICK = 100;
    private static final int RETAIN_FROM_TICK = 1000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        int[] retained0 = null;
        int[] retained1 = null;
        int[] retained2 = null;
        int[] retained3 = null;
        int[] retained4 = null;
        int[] retained5 = null;
        int[] retained6 = null;
        int[] retained7 = null;
        int[] retained8 = null;
        int[] retained9 = null;
        int[] retained10 = null;
        int[] retained11 = null;
        int[] retained12 = null;
        int[] retained13 = null;
        int[] retained14 = null;
        int[] retained15 = null;
        int[] retained16 = null;
        int[] retained17 = null;
        int[] retained18 = null;
        int[] retained19 = null;
        int[] retained20 = null;
        int[] retained21 = null;
        int[] retained22 = null;
        int[] retained23 = null;
        int[] retained24 = null;

        int tick = 0;
        while (true) {
            // Allocated and touched every tick. Before RETAIN_FROM_TICK it's discarded (nothing
            // keeps it reachable past this point, so it's garbage the instant the next tick
            // starts). From RETAIN_FROM_TICK on, it's stashed into the next unused retainedN local
            // instead, so it stays reachable forever — the collector can no longer reclaim it.
            int[] scratch = new int[INTS_PER_TICK];
            scratch[0] = tick;

            int retainSlot = tick - RETAIN_FROM_TICK;
            if (retainSlot == 0) {
                retained0 = scratch;
            } else if (retainSlot == 1) {
                retained1 = scratch;
            } else if (retainSlot == 2) {
                retained2 = scratch;
            } else if (retainSlot == 3) {
                retained3 = scratch;
            } else if (retainSlot == 4) {
                retained4 = scratch;
            } else if (retainSlot == 5) {
                retained5 = scratch;
            } else if (retainSlot == 6) {
                retained6 = scratch;
            } else if (retainSlot == 7) {
                retained7 = scratch;
            } else if (retainSlot == 8) {
                retained8 = scratch;
            } else if (retainSlot == 9) {
                retained9 = scratch;
            } else if (retainSlot == 10) {
                retained10 = scratch;
            } else if (retainSlot == 11) {
                retained11 = scratch;
            } else if (retainSlot == 12) {
                retained12 = scratch;
            } else if (retainSlot == 13) {
                retained13 = scratch;
            } else if (retainSlot == 14) {
                retained14 = scratch;
            } else if (retainSlot == 15) {
                retained15 = scratch;
            } else if (retainSlot == 16) {
                retained16 = scratch;
            } else if (retainSlot == 17) {
                retained17 = scratch;
            } else if (retainSlot == 18) {
                retained18 = scratch;
            } else if (retainSlot == 19) {
                retained19 = scratch;
            } else if (retainSlot == 20) {
                retained20 = scratch;
            } else if (retainSlot == 21) {
                retained21 = scratch;
            } else if (retainSlot == 22) {
                retained22 = scratch;
            } else if (retainSlot == 23) {
                retained23 = scratch;
            } else if (retainSlot == 24) {
                retained24 = scratch;
            }

            Serial.print("tick ");
            Serial.println(tick);

            tick = tick + 1;
            Delay.millis(50);
        }
    }
}
