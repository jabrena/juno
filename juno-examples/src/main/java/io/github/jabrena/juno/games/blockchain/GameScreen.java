package io.github.jabrena.juno.games.blockchain;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Drawing helpers shared by every screen of {@link BlockchainGame}: a title bar, status rows and the
 * MENU button that takes a running role back to the role menu.
 */
final class GameScreen {
    static final int BACKGROUND = 0x0841;
    static final int PANEL = 0x18C3;
    static final int MENU_Y = 280;

    private static final int FIRST_ROW_Y = 54;
    private static final int ROW_HEIGHT = 22;

    private GameScreen() {
    }

    static void message(String text, int color) {
        TftTouchShield.fillScreen(BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, BACKGROUND);
        TftTouchShield.setCursor(22, 145);
        TftTouchShield.print(text);
    }

    /** Clears the screen and draws {@code text} in the title bar and the MENU button at the bottom. */
    static void role(String text, int color) {
        TftTouchShield.fillScreen(BACKGROUND);
        title(text, color);
        TftTouchShield.fillRect(20, MENU_Y, 200, 34, PANEL);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, PANEL);
        TftTouchShield.setCursor(96, MENU_Y + 10);
        TftTouchShield.print("MENU");
    }

    static void title(String text, int color) {
        TftTouchShield.fillRect(0, 0, 240, 44, PANEL);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, PANEL);
        TftTouchShield.setCursor(16, 15);
        TftTouchShield.print(text);
    }

    /**
     * Clears status row {@code index} (0 to 9) and leaves the cursor at its start, with text of
     * {@code size} (1 or 2) in {@code color}.
     */
    static void row(int index, int size, int color) {
        int y = FIRST_ROW_Y + index * ROW_HEIGHT;
        TftTouchShield.fillRect(0, y, 240, ROW_HEIGHT, BACKGROUND);
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, BACKGROUND);
        TftTouchShield.setCursor(12, size == 2 ? y + 3 : y + 7);
    }

    /** Prints {@code p}, or {@code p^e} when {@code e > 1}, preceded by {@code " x "} unless it is the first factor. */
    static void factor(long prime, int exponent, boolean first) {
        if (!first) {
            TftTouchShield.print(" x ");
        }
        TftTouchShield.print("" + prime);
        if (exponent > 1) {
            TftTouchShield.print("^" + exponent);
        }
    }

    /** Prints the first eight bytes of the hash at {@code hash[offset..)} in hex, followed by "...". */
    static void hashPrefix(byte[] hash, int offset) {
        TftTouchShield.print(LedgerIo.hexWord(hash, offset));
        TftTouchShield.print(LedgerIo.hexWord(hash, offset + 4));
        TftTouchShield.print("...");
    }

    /** Whether the MENU button was tapped; never waits when the screen is not being touched. */
    static boolean menuTapped() {
        if (!TftTouchShield.readTouch() || TftTouchShield.touchY() < MENU_Y) {
            return false;
        }
        waitForRelease();
        return true;
    }

    static void waitForRelease() {
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
