package io.github.jabrena.juno.api.lcd.pomodoro;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

/**
 * The session-length picker: row 0 shows "Pomodoro NNm" live while UP/DOWN adjust it in
 * {@link #MINUTES_STEP}-minute steps (clamped to {@link #MIN_MINUTES}-{@link #MAX_MINUTES}); row 1
 * shows the button hints. SELECT confirms and returns the chosen duration.
 */
final class ConfigurePomodoroView {
    private static final int MIN_MINUTES = 5;
    private static final int MAX_MINUTES = 60;
    private static final int MINUTES_STEP = 5;
    private static final int DEFAULT_MINUTES = 25;
    private static final int BUTTON_POLL_MILLIS = 50;

    private ConfigurePomodoroView() {
    }

    /** Runs the picker until SELECT is pressed, returning the chosen session length in minutes. */
    static int run() {
        int minutes = DEFAULT_MINUTES;
        int lastButton = LcdKeypadShield.NONE;
        show(minutes);

        while (true) {
            int button = LcdKeypadShield.readButton();
            if (button != lastButton) {
                if (button == LcdKeypadShield.UP && minutes < MAX_MINUTES) {
                    minutes = minutes + MINUTES_STEP;
                    show(minutes);
                } else if (button == LcdKeypadShield.DOWN && minutes > MIN_MINUTES) {
                    minutes = minutes - MINUTES_STEP;
                    show(minutes);
                } else if (button == LcdKeypadShield.SELECT) {
                    Serial.print("Selected duration: ");
                    Serial.print(minutes);
                    Serial.println(" minutes");
                    return minutes;
                }
                lastButton = button;
            }
            Delay.millis(BUTTON_POLL_MILLIS);
        }
    }

    private static void show(int minutes) {
        LcdKeypadShield.clear();
        LcdKeypadShield.setCursor(0, 0);
        LcdKeypadShield.print("Pomodoro ");
        LcdKeypadShield.print(minutes);
        LcdKeypadShield.print("m");
        LcdKeypadShield.setCursor(0, 1);
        LcdKeypadShield.print("UP/DOWN, SELECT");
    }
}
