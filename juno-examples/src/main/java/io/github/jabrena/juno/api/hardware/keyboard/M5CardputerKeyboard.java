package io.github.jabrena.juno.api.hardware.keyboard;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Shows keyboard transitions on the Cardputer display and reports keys and modifiers over USB serial. */
@Board(M5StackCardputerBoard.class)
@SuppressWarnings("java:S2189")
public final class M5CardputerKeyboard {
    private static final int TEXT_CAPACITY = 72;

    private M5CardputerKeyboard() {
    }

    /** Polls the built-in keyboard without allocating in the main loop. */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        char[] typedText = new char[TEXT_CAPACITY];
        int typedLength = 0;

        Display display = M5StackCardputer.getDisplay();
        display.begin();
        display.fillScreen(Display.BLACK);
        display.setCursor(8, 24);
        display.setTextColor(Display.GREEN, Display.BLACK);
        display.setTextSize(2);
        display.print('>');
        display.print(' ');

        Keyboard keyboard = M5StackCardputer.getKeyboard();
        keyboard.begin();
        while (true) {
            keyboard.update();
            if (keyboard.isChanged()) {
                boolean textChanged = false;
                if (keyboard.isPressed()) {
                    if (keyboard.isShiftPressed()) {
                        Serial.println("Shift pressed");
                    }
                    if (keyboard.isControlPressed()) {
                        Serial.println("Control pressed");
                    }
                    if (keyboard.isAltPressed()) {
                        Serial.println("Alt pressed");
                    }
                    if (keyboard.isFnPressed()) {
                        Serial.println("Fn pressed");
                    }
                    if (keyboard.isOptionPressed()) {
                        Serial.println("Option pressed");
                    }
                    for (int index = 0; index < keyboard.keyCount(); index++) {
                        int key = keyboard.keyAt(index);
                        Serial.println(key);
                        if (key == Keyboard.BACKSPACE) {
                            if (typedLength > 0) {
                                typedLength--;
                                textChanged = true;
                            }
                        } else if (key == Keyboard.ENTER || key == Keyboard.TAB || (key >= ' ' && key <= '~')) {
                            if (typedLength == typedText.length) {
                                for (int position = 1; position < typedLength; position++) {
                                    typedText[position - 1] = typedText[position];
                                }
                                typedLength--;
                            }
                            char character = (char) key;
                            if (key == Keyboard.ENTER) {
                                character = '\n';
                            } else if (key == Keyboard.TAB) {
                                character = ' ';
                            }
                            typedText[typedLength++] = character;
                            textChanged = true;
                        }
                    }
                }
                if (textChanged) {
                    display.fillScreen(Display.BLACK);
                    display.setCursor(8, 24);
                    display.print('>');
                    display.print(' ');
                    for (int index = 0; index < typedLength; index++) {
                        display.print(typedText[index]);
                    }
                }
            }
            Delay.millis(20);
        }
    }
}
