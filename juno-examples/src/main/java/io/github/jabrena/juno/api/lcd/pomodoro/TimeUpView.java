package io.github.jabrena.juno.api.lcd.pomodoro;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

/** The alarm screen: both rows announce the session is over and the backlight blinks until any button is pressed. */
final class TimeUpView {
    private static final int BLINK_MILLIS = 400;

    private TimeUpView() {
    }

    /** Shows the alarm and blinks the backlight until any button is pressed, then returns. */
    static void run() {
        Serial.println("ALARM: Pomodoro time is up");
        LcdKeypadShield.clear();
        LcdKeypadShield.setCursor(0, 0);
        LcdKeypadShield.print("TIME'S UP!");
        LcdKeypadShield.setCursor(0, 1);
        LcdKeypadShield.print("Take a break!");

        boolean backlightOn = true;
        while (LcdKeypadShield.readButton() == LcdKeypadShield.NONE) {
            backlightOn = !backlightOn;
            LcdKeypadShield.backlight(backlightOn);
            Delay.millis(BLINK_MILLIS);
        }
        LcdKeypadShield.backlight(true);
        Serial.println("Button pressed; returning to minute selection");
    }
}
