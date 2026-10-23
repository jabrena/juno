package io.github.jabrena.juno.m5stack;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5Cardputer;
import io.github.jabrena.juno.hardware.M5;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Shows keyboard transitions on the Cardputer display and sends each key code over USB serial. */
@Board(M5Cardputer.class)
public final class M5CardputerKeyboard {
    private M5CardputerKeyboard() {
    }

    /** Polls the built-in keyboard without allocating in the main loop. */
    public static void main(String[] args) {
        Serial.begin(9600);
        Delay.millis(1000);

        Display display = M5.getDisplay();
        display.begin();
        display.fillScreen(Display.BLACK);
        display.setCursor(12, 24);
        display.setTextColor(Display.GREEN, Display.BLACK);
        display.setTextSize(2);
        display.println("Press a key");

        Keyboard keyboard = M5.getKeyboard();
        keyboard.begin();
        while (true) {
            keyboard.update();
            if (keyboard.isChanged()) {
                display.fillScreen(Display.BLACK);
                display.setCursor(12, 24);
                if (keyboard.isPressed()) {
                    display.println("Key pressed");
                    for (int index = 0; index < keyboard.keyCount(); index++) {
                        Serial.println(keyboard.keyAt(index));
                    }
                } else {
                    display.println("Press a key");
                }
            }
            Delay.millis(20);
        }
    }
}
