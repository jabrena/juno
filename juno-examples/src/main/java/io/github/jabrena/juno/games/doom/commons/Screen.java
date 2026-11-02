package io.github.jabrena.juno.games.doom.commons;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * Drawing shared by Doom's views on the landscape 320x240 screen: centered text, DOOM's red-edged plaque and the
 * BACK button. One copy keeps the code small: on the UNO Q the program's code counts against the same RAM as the
 * engine's map tables.
 */
public final class Screen {
    public static final int WIDTH = 320;
    public static final int BACKGROUND = TftTouchShield.BLACK;
    public static final int BUTTON = 0x2124;

    private static final int BACK_X = 8;
    private static final int BACK_Y = 204;
    private static final int BACK_WIDTH = 64;
    private static final int BACK_HEIGHT = 28;
    private static final int EDGE = TftTouchShield.color(215, 70, 30);
    private static final int EDGE_DARK = TftTouchShield.color(120, 25, 12);

    private Screen() {
    }

    /** Prints {@code text} centered at row {@code y}, over the black background. */
    public static void showCentered(TftTouchShield tft, String text, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, BACKGROUND);
        tft.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        tft.print(text);
    }

    /** A dark plaque with a double red edge, as DOOM frames its titles and prompts. */
    public static void plaque(TftTouchShield tft, int x, int y, int width, int height) {
        tft.fillRect(x, y, width, height, BACKGROUND);
        tft.drawRect(x, y, width, height, EDGE);
        tft.drawRect(x + 2, y + 2, width - 4, height - 4, EDGE_DARK);
    }

    /** The BACK button at the bottom left. */
    public static void drawBack(TftTouchShield tft) {
        tft.fillRect(BACK_X, BACK_Y, BACK_WIDTH, BACK_HEIGHT, BUTTON);
        tft.drawRect(BACK_X, BACK_Y, BACK_WIDTH, BACK_HEIGHT, TftTouchShield.WHITE);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, BUTTON);
        tft.setCursor(BACK_X + 8, BACK_Y + 7);
        tft.print("BACK");
    }

    /** Whether ({@code x}, {@code y}) is on the BACK button. */
    public static boolean isBack(int x, int y) {
        return x >= BACK_X && x < BACK_X + BACK_WIDTH && y >= BACK_Y && y < BACK_Y + BACK_HEIGHT;
    }
}
