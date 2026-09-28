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
 * forever (stashed into the next free slot of {@code retained}, a fixed-size {@code int[][]} whose
 * slots never get overwritten — the collector scans every word of a live array's payload, so each
 * block a slot points at stays reachable for as long as {@code retained} itself does). A conservative collector can
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
    private static final int RETAINED_SLOTS = 25;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        int[][] retained = new int[RETAINED_SLOTS][];

        int tick = 0;
        while (true) {
            // Allocated and touched every tick. Before RETAIN_FROM_TICK it's discarded (nothing
            // keeps it reachable past this point, so it's garbage the instant the next tick
            // starts). From RETAIN_FROM_TICK on, it's stashed into the next unused retained slot
            // instead, so it stays reachable forever — the collector can no longer reclaim it.
            int[] scratch = new int[INTS_PER_TICK];
            scratch[0] = tick;

            int retainSlot = tick - RETAIN_FROM_TICK;
            if (retainSlot >= 0 && retainSlot < RETAINED_SLOTS) {
                retained[retainSlot] = scratch;
            }

            Serial.print("tick ");
            Serial.println(tick);

            tick = tick + 1;
            Delay.millis(50);
        }
    }
}
