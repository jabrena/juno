package io.github.jabrena.juno.usecases.miner.commons;

import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.util.mvc.Navigator;

/**
 * The row of tabs along the bottom of the portrait 240x320 screen, one per view in {@link Miner2Views} order. Every
 * view draws it with its own tab highlighted and hands it the touches, so one copy of the drawing and hit-testing
 * code serves all four. Each tab is as wide as its label needs, so every label keeps the size-2 font. Stopping is not
 * a tab: it is the Wi-Fi icon in the header.
 */
public final class TabBar {
    public static final int TOP = GameScreen.MENU_Y;
    public static final int TABS = 4;

    /** Where each tab starts, and the right edge of the screen after the last one. */
    private static final int[] EDGES = {0, 74, 136, 198, 240};
    private static final int HEIGHT = 320 - TOP;
    private static final int GAP = 1;
    private static final int SELECTED = 0x0320;

    private TabBar() {
    }

    /** The tab at ({@code x}, {@code y}), which is also its view's id, or -1 above the bar or off the screen. */
    public static int tabAt(int x, int y) {
        if (y < TOP || x < 0) {
            return -1;
        }
        for (int tab = 0; tab < TABS; tab++) {
            if (x < EDGES[tab + 1]) {
                return tab;
            }
        }
        return -1;
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
        TftTouchShield.fillRect(0, TOP, EDGES[TABS], HEIGHT, GameScreen.BACKGROUND);
        for (int tab = 0; tab < TABS; tab++) {
            String label = label(tab);
            int x = EDGES[tab];
            int width = EDGES[tab + 1] - x;
            int color = tab == selected ? SELECTED : GameScreen.PANEL;
            TftTouchShield.fillRect(x + GAP, TOP + GAP, width - 2 * GAP, HEIGHT - 2 * GAP, color);
            if (tab > 0) {
                // The labels nearly fill their tabs, so a divider keeps neighbours apart.
                TftTouchShield.drawVerticalLine(x, TOP + 6, HEIGHT - 12, TftTouchShield.GRAY);
            }
            TftTouchShield.setTextSize(2);
            TftTouchShield.setTextColor(tab == selected ? TftTouchShield.WHITE : TftTouchShield.GRAY, color);
            // A size-2 glyph is 12 pixels wide, the last two of them blank spacing.
            TftTouchShield.setCursor(x + (width - label.length() * 12 + 2) / 2, TOP + (HEIGHT - 14) / 2);
            TftTouchShield.print(label);
        }
    }

    private static String label(int tab) {
        if (tab == Miner2Views.BLOCKS) {
            return "BLOCKS";
        }
        if (tab == Miner2Views.CHAIN) {
            return "CHAIN";
        }
        if (tab == Miner2Views.PEERS) {
            return "PEERS";
        }
        return "NET";
    }
}
