package io.github.jabrena.juno.games.common;

import io.github.jabrena.juno.hardware.ArduinoUno;
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

    private static final Clock CLOCK = ArduinoUno.getClock();

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
    public static void drawChoice(TftTouchShield tft,
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
        tft.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, fill);
        tft.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, chosen ? chosenBorder : unchosenBorder);
        tft.setTextSize(3);
        tft.setTextColor(labelColor, fill);
        tft.setCursor(x + (CHOICE_WIDTH - label.length() * 18) / 2, CHOICE_Y + 16);
        tft.print(label);
        tft.setTextSize(1);
        tft.setTextColor(hintColor, fill);
        tft.setCursor(x + (CHOICE_WIDTH - hint.length() * 6) / 2, CHOICE_Y + 52);
        tft.print(hint);
    }

    /** Waits until a pilot choice is tapped. */
    public static int waitForChoice(TftTouchShield tft) {
        int choice = -1;
        while (choice < 0) {
            if (tft.readTouch()) {
                choice = choiceAt(tft.touchX(), tft.touchY());
            }
            Delay.millis(10);
        }
        return choice;
    }

    /** Waits for a tap, giving up and returning {@code false} if none arrives within the timeout. */
    public static boolean waitForTap(TftTouchShield tft, int timeoutMillis) {
        int start = CLOCK.millis();
        while (!tft.readTouch()) {
            if (CLOCK.millis() - start >= timeoutMillis) {
                return false;
            }
            Delay.millis(10);
        }
        waitForRelease(tft);
        return true;
    }

    /** Waits for three consecutive frames without contact. */
    public static void waitForRelease(TftTouchShield tft) {
        int misses = 0;
        while (misses < 3) {
            if (tft.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
