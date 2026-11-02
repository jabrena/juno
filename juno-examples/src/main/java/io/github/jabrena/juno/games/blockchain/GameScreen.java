package io.github.jabrena.juno.games.blockchain;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/**
 * Drawing helpers shared by the blockchain games' screens: a title bar, status rows and the MENU button
 * that takes a running role back to its game's role menu.
 */
public final class GameScreen {
    public static final int BACKGROUND = 0x0841;
    public static final int PANEL = 0x18C3;
    public static final int MENU_Y = 280;

    private static final int FIRST_ROW_Y = 54;
    private static final int ROW_HEIGHT = 22;

    private GameScreen() {
    }

    public static void message(TftTouchShield tft, String text, int color) {
        tft.fillScreen(BACKGROUND);
        tft.setTextSize(2);
        tft.setTextColor(color, BACKGROUND);
        tft.setCursor(22, 145);
        tft.print(text);
    }

    /** Clears the screen and draws {@code text} in the title bar. */
    public static void header(TftTouchShield tft, String text, int color) {
        tft.fillScreen(BACKGROUND);
        title(tft, text, color);
    }

    /** Like {@link #header}, plus the MENU button at the bottom that {@link #menuTapped} watches. */
    public static void role(TftTouchShield tft, String text, int color) {
        header(tft, text, color);
        tft.fillRect(20, MENU_Y, 200, 34, PANEL);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, PANEL);
        tft.setCursor(96, MENU_Y + 10);
        tft.print("MENU");
    }

    public static void title(TftTouchShield tft, String text, int color) {
        tft.fillRect(0, 0, 240, 44, PANEL);
        tft.setTextSize(2);
        tft.setTextColor(color, PANEL);
        tft.setCursor(16, 15);
        tft.print(text);
    }

    /**
     * Clears status row {@code index} (0 to 9) and leaves the cursor at its start, with text of
     * {@code size} (1 or 2) in {@code color}.
     */
    public static void row(TftTouchShield tft, int index, int size, int color) {
        int y = FIRST_ROW_Y + index * ROW_HEIGHT;
        tft.fillRect(0, y, 240, ROW_HEIGHT, BACKGROUND);
        tft.setTextSize(size);
        tft.setTextColor(color, BACKGROUND);
        tft.setCursor(12, size == 2 ? y + 3 : y + 7);
    }

    /** Prints {@code p}, or {@code p^e} when {@code e > 1}, preceded by {@code " x "} unless it is the first factor. */
    public static void factor(TftTouchShield tft, long prime, int exponent, boolean first) {
        if (!first) {
            tft.print(" x ");
        }
        tft.print("" + prime);
        if (exponent > 1) {
            tft.print("^" + exponent);
        }
    }

    /** Prints the first eight bytes of the hash at {@code hash[offset..)} in hex, followed by "...". */
    public static void hashPrefix(TftTouchShield tft, byte[] hash, int offset) {
        tft.print(NodeIo.hexWord(hash, offset));
        tft.print(NodeIo.hexWord(hash, offset + 4));
        tft.print("...");
    }

    /** Whether the MENU button was tapped; never waits when the screen is not being touched. */
    public static boolean menuTapped(TftTouchShield tft) {
        if (!tft.readTouch() || tft.touchY() < MENU_Y) {
            return false;
        }
        waitForRelease(tft);
        return true;
    }

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
