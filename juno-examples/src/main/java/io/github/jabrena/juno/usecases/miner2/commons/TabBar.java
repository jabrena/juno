package io.github.jabrena.juno.usecases.miner2.commons;

import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.blockchain.GameScreen;

/**
 * The row of tabs along the bottom of the portrait 240x320 screen, one per view in {@link Miner2Views} order. Every
 * view draws it with its own tab highlighted and hands it the touches, so one copy of the drawing and hit-testing
 * code serves all three.
 */
public final class TabBar {
    public static final int TOP = GameScreen.MENU_Y;
    public static final int TABS = 3;

    private static final int TAB_WIDTH = 80;
    private static final int HEIGHT = 320 - TOP;
    private static final int GAP = 2;
    private static final int SELECTED = 0x0320;

    private TabBar() {
    }

    /** The tab at ({@code x}, {@code y}), which is also its view's id, or -1 above the bar or off the screen. */
    public static int tabAt(int x, int y) {
        if (y < TOP || x < 0 || x >= TABS * TAB_WIDTH) {
            return -1;
        }
        return x / TAB_WIDTH;
    }

    /** Shows the view whose tab is at ({@code x}, {@code y}); does nothing for a touch elsewhere. */
    public static void follow(Navigator navigator, int x, int y) {
        int tab = tabAt(x, y);
        if (tab >= 0) {
            navigator.show(tab);
        }
    }

    /** Draws every tab, highlighting {@code selected}. */
    public static void draw(int selected) {
        TftTouchShield.fillRect(0, TOP, TABS * TAB_WIDTH, HEIGHT, GameScreen.BACKGROUND);
        for (int tab = 0; tab < TABS; tab++) {
            String label = label(tab);
            int x = tab * TAB_WIDTH;
            int color = tab == selected ? SELECTED : GameScreen.PANEL;
            TftTouchShield.fillRect(x + GAP, TOP + GAP, TAB_WIDTH - 2 * GAP, HEIGHT - 2 * GAP, color);
            TftTouchShield.setTextSize(2);
            TftTouchShield.setTextColor(tab == selected ? TftTouchShield.WHITE : TftTouchShield.GRAY, color);
            TftTouchShield.setCursor(x + (TAB_WIDTH - label.length() * 12) / 2, TOP + (HEIGHT - 14) / 2);
            TftTouchShield.print(label);
        }
    }

    private static String label(int tab) {
        if (tab == Miner2Views.CHAIN) {
            return "CHAIN";
        }
        return tab == Miner2Views.PEERS ? "PEERS" : "NET";
    }
}
