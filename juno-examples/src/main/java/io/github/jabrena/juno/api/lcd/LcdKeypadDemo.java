package io.github.jabrena.juno.api.lcd;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Prints a header on row 0 of the LCD Keypad Shield's screen, then shows which of its 5 buttons
 * (if any) is currently held on row 1, updating only when the pressed button changes.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class LcdKeypadDemo {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println(1);
        LcdKeypadShield.begin();
        Serial.println(2);
        LcdKeypadShield.print("Press a button");
        Serial.println(3);

        int lastButton = -1;
        while (true) {
            int button = LcdKeypadShield.readButton();
            Serial.println(button);
            if (button != lastButton) {
                LcdKeypadShield.setCursor(0, 1);
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
                LcdKeypadShield.print(label);
                lastButton = button;
            }
            Delay.millis(50);
        }
    }
}
