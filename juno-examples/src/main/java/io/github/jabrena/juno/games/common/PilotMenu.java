package io.github.jabrena.juno.games.common;

import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/** Shared two-choice pilot menu geometry, rendering, and touch timing. */
public final class PilotMenu {
    public static final int CHOICE_X = 20;
    public static final int CHOICE_Y = 104;
    public static final int CHOICE_WIDTH = 130;
    public static final int CHOICE_HEIGHT = 72;
    public static final int CHOICE_GAP = 20;

    private static final Clock CLOCK = ArduinoOne.getClock();

    private PilotMenu() {
    }

    /** The pilot button at {@code (x, y):} 0 for HUMAN, 1 for CPU, or -1. */
    public static int choiceAt(int x, int y) {
        if (y < CHOICE_Y || y >= CHOICE_Y + CHOICE_HEIGHT) {
            return -1;
        }
        for (int index = 0; index < 2; index++) {
            int left = CHOICE_X + index * (CHOICE_WIDTH + CHOICE_GAP);
            if (x >= left && x < left + CHOICE_WIDTH) {
                return index;
            }
        }
        return -1;
    }

    /** Draws one of the two pilot choices with the colors selected by the game. */
    public static void drawChoice(
            int index,
            String label,
            String hint,
            boolean chosen,
            int chosenFill,
            int unchosenFill,
            int chosenBorder,
            int unchosenBorder,
            int labelColor,
            int hintColor) {
        int x = CHOICE_X + index * (CHOICE_WIDTH + CHOICE_GAP);
        int fill = chosen ? chosenFill : unchosenFill;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, fill);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, chosen ? chosenBorder : unchosenBorder);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(labelColor, fill);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 18) / 2, CHOICE_Y + 16);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(hintColor, fill);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - hint.length() * 6) / 2, CHOICE_Y + 52);
        TftTouchShield.print(hint);
    }

    /** Waits until a pilot choice is tapped. */
    public static int waitForChoice() {
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        return choice;
    }

    /** Waits for a tap, giving up and returning {@code false} if none arrives within the timeout. */
    public static boolean waitForTap(int timeoutMillis) {
        int start = CLOCK.millis();
        while (!TftTouchShield.readTouch()) {
            if (CLOCK.millis() - start >= timeoutMillis) {
                return false;
            }
            Delay.millis(10);
        }
        waitForRelease();
        return true;
    }

    /** Waits for three consecutive frames without contact. */
    public static void waitForRelease() {
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
