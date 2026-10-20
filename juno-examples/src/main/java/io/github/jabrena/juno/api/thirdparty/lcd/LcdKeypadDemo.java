package io.github.jabrena.juno.api.thirdparty.lcd;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.thirdparty.lcd.LcdKeypadShield;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints a header on row 0 of the LCD Keypad Shield's screen, then shows which of its 5 buttons
 * (if any) is currently held on row 1, updating only when the pressed button changes.
 */
public final class LcdKeypadDemo {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        LcdKeypadShield lcd = new LcdKeypadShield();
        lcd.begin();
        lcd.print("Press a button");

        int lastButton = -1;
        while (true) {
            int button = lcd.readButton();
            Serial.println(button);
            if (button != lastButton) {
                lcd.setCursor(0, 1);
                String label = "NONE  ";
                if (button == LcdKeypadShield.RIGHT) {
                    label = "RIGHT ";
                } else if (button == LcdKeypadShield.UP) {
                    label = "UP    ";
                } else if (button == LcdKeypadShield.DOWN) {
                    label = "DOWN  ";
                } else if (button == LcdKeypadShield.LEFT) {
                    label = "LEFT  ";
                } else if (button == LcdKeypadShield.SELECT) {
                    label = "SELECT";
                }
                lcd.print(label);
                lastButton = button;
            }
            Delay.millis(50);
        }
    }
}
