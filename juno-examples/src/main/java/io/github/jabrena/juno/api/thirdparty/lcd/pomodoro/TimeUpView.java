package io.github.jabrena.juno.api.thirdparty.lcd.pomodoro;

import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.thirdparty.lcd.LcdKeypadShield;
import io.github.jabrena.juno.api.util.Delay;

/** The alarm screen: both rows announce the session is over and the backlight blinks until any button is pressed. */
final class TimeUpView {
    private static final int BLINK_MILLIS = 400;

    private TimeUpView() {
    }

    /** Shows the alarm and blinks the backlight until any button is pressed, then returns. */
    static void run() {
        Serial.println("ALARM: Pomodoro time is up");
        Pomodoro.LCD_SHIELD.clear();
        Pomodoro.LCD_SHIELD.setCursor(0, 0);
        Pomodoro.LCD_SHIELD.print("TIME'S UP!");
        Pomodoro.LCD_SHIELD.setCursor(0, 1);
        Pomodoro.LCD_SHIELD.print("Take a break!");

        boolean backlightOn = true;
        while (Pomodoro.LCD_SHIELD.readButton() == LcdKeypadShield.NONE) {
            backlightOn = !backlightOn;
            Pomodoro.LCD_SHIELD.backlight(backlightOn);
            Delay.millis(BLINK_MILLIS);
        }
        Pomodoro.LCD_SHIELD.backlight(true);
        Serial.println("Button pressed; returning to minute selection");
    }
}
