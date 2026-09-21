package io.github.jabrena.juno.api.lcd.pomodoro;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

/**
 * The live countdown: row 0 shows "Pomodoro", row 1 shows "Time left: MM:SS", updating once a
 * second until the session's duration elapses. {@link Clock#millis()} is the sole time source (a
 * local monotonic clock), so accuracy doesn't depend on how long each tick's own work takes.
 */
final class RunPomodoro {
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int MILLIS_PER_SECOND = 1000;
    private static final int TICK_MILLIS = 200;

    private RunPomodoro() {
    }

    /** Counts down from {@code durationMinutes} to zero, returning once the session ends. */
    static void run(int durationMinutes) {
        int totalSeconds = durationMinutes * SECONDS_PER_MINUTE;
        int startMillis = Clock.millis();

        int lastRemainingSeconds = -1;
        while (true) {
            int elapsedSeconds = (Clock.millis() - startMillis) / MILLIS_PER_SECOND;
            int remainingSeconds = totalSeconds - elapsedSeconds;
            if (remainingSeconds < 0) {
                break;
            }

            if (remainingSeconds != lastRemainingSeconds) {
                int minutes = remainingSeconds / SECONDS_PER_MINUTE;
                int seconds = remainingSeconds % SECONDS_PER_MINUTE;
                show(minutes, seconds);
                Serial.print("Tick: ");
                Serial.print(minutes);
                Serial.print(":");
                Serial.println(seconds);
                lastRemainingSeconds = remainingSeconds;
            }

            Delay.millis(TICK_MILLIS);
        }
    }

    /**
     * Fully clears and redraws both rows on every tick, rather than overwriting row 1 in place: a
     * single glitched write self-heals on the very next tick instead of persisting on screen
     * indefinitely. Row 0 gets an explicit {@code setCursor(0, 0)} rather than relying on
     * {@code clear()}'s own return-to-home behavior: row 1 (which already sets its cursor
     * explicitly) never drifted, while row 0 (which didn't) was observed walking right by a column
     * over time — an explicit cursor set before every write is the same self-correcting pattern,
     * applied consistently.
     */
    private static void show(int minutes, int seconds) {
        LcdKeypadShield.clear();
        LcdKeypadShield.setCursor(0, 0);
        LcdKeypadShield.print("Pomodoro");
        LcdKeypadShield.setCursor(0, 1);
        LcdKeypadShield.print("Time left: ");
        printTwoDigits(minutes);
        LcdKeypadShield.print(":");
        printTwoDigits(seconds);
    }

    private static void printTwoDigits(int value) {
        if (value < 10) {
            LcdKeypadShield.print("0");
        }
        LcdKeypadShield.print(value);
    }
}
