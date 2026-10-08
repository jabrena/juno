package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The header above the view: map name, the pilot (CPU or HUMAN), health, armor and kills. */
final class Hud {
    private static int shownHealth = Integer.MIN_VALUE;
    private static int shownKills = Integer.MIN_VALUE;
    private static int shownArmor = Integer.MIN_VALUE;

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
        TftTouchShield.print(Controls.autopilot ? "CPU  " : "HUMAN");
        shownHealth = Integer.MIN_VALUE;
        drawStatus();
    }

    static void drawStatus() {
        if (Player.health == shownHealth && Monsters.kills == shownKills && Player.armor == shownArmor) {
            return;
        }
        shownHealth = Player.health;
        shownKills = Monsters.kills;
        shownArmor = Player.armor;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(Player.health > 25 ? TftTouchShield.GREEN : TftTouchShield.RED,
                DisplayList.BACKGROUND);
        TftTouchShield.setCursor(196, 2);
        TftTouchShield.print("HEALTH ");
        TftTouchShield.print(Player.health);
        TftTouchShield.print("%  ");
        TftTouchShield.setTextColor(TftTouchShield.WHITE, DisplayList.BACKGROUND);
        TftTouchShield.setCursor(196, 11);
        TftTouchShield.print("KILLS ");
        TftTouchShield.print(Monsters.kills);
        TftTouchShield.print("  ");
        TftTouchShield.setTextColor(Player.armorClass == 2 ? Sprites.BLUE_ARMOR_COLOR : Sprites.GREEN_ARMOR_COLOR,
                DisplayList.BACKGROUND);
        TftTouchShield.setCursor(100, 11);
        TftTouchShield.print("ARMOR ");
        TftTouchShield.print(Player.armor);
        TftTouchShield.print("%  ");
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, DisplayList.BACKGROUND);
        TftTouchShield.setCursor((DisplayList.WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
