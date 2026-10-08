package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The header above the view: map name, who is walking, and the marine's position. */
final class Hud {
    private static int shownX = Integer.MIN_VALUE;
    private static int shownY = Integer.MIN_VALUE;

    private Hud() {
    }

    static void drawHeader() {
        TftTouchShield.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, DisplayList.BACKGROUND);
        TftTouchShield.drawHorizontalLine(0, DisplayList.HEADER - 1, DisplayList.WIDTH, Renderer.WALL_FAR);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.RED, DisplayList.BACKGROUND);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("DOOM ");
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, DisplayList.BACKGROUND);
        TftTouchShield.print(Level.NAME);
        TftTouchShield.setTextColor(Controls.autopilot ? TftTouchShield.MAGENTA : TftTouchShield.CYAN,
                DisplayList.BACKGROUND);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print(Controls.autopilot ? "AUTOPILOT" : "MANUAL   ");
        shownX = Integer.MIN_VALUE;
        drawStatus();
    }

    static void drawStatus() {
        int x = Math.round(Player.x);
        int y = Math.round(Player.y);
        if (x == shownX && y == shownY) {
            return;
        }
        shownX = x;
        shownY = y;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, DisplayList.BACKGROUND);
        TftTouchShield.setCursor(196, 2);
        TftTouchShield.print("X ");
        TftTouchShield.print(x);
        TftTouchShield.print("    ");
        TftTouchShield.setCursor(196, 11);
        TftTouchShield.print("Y ");
        TftTouchShield.print(y);
        TftTouchShield.print("    ");
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, DisplayList.BACKGROUND);
        TftTouchShield.setCursor((DisplayList.WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
