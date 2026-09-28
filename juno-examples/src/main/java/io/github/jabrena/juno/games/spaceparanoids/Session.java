package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Score, lives, sector, shield, timer and the current sector's remaining hunters. */
final class Session {
    static final int MAX_SHIELD = 100;
    private static final int START_LIVES = 3;
    private static final int BONUS_EVERY = 10000;

    static final int HUNTER_POINTS = 1000;
    static final int TANK_POINTS = 500;
    static final int TURRET_POINTS = 250;
    static final int POOL_POINTS = 100;

    static int score;
    static int best;
    static int lives;
    static int level;
    static int nextBonus;
    static int shield;
    static int timeLeft;
    static int huntersLeft;
    static int frame;

    private Session() {
    }

    static void newGame() {
        score = 0;
        lives = START_LIVES;
        level = 1;
        nextBonus = BONUS_EVERY;
    }

    static void endGame() {
        if (score > best) {
            best = score;
        }
    }

    static int levelTime() {
        return Math.max(60, 130 - 10 * level);
    }

    static void loseLife(boolean timeUp) {
        lives = lives - 1;
        for (int r = 4; r < Camera.CENTER_Y - DisplayList.HEADER; r = r + 6) {
            TftTouchShield.drawCircle(Camera.CENTER_X, Camera.CENTER_Y, r,
                    (r & 4) == 0 ? TftTouchShield.RED : TftTouchShield.ORANGE);
            Delay.millis(15);
        }
        DisplayList.clearView();
        Hud.showCentered(timeUp ? "TIME UP" : "TANK DESTROYED", 90, 2, TftTouchShield.RED);
        Hud.drawHeader();
        Delay.millis(1500);
    }

    static void addScore(int points) {
        score = score + points;
        if (score >= nextBonus) {
            lives = lives + 1;
            nextBonus = nextBonus + BONUS_EVERY;
        }
    }

    static void damage(int amount) {
        shield = Math.max(0, shield - amount);
        TftTouchShield.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, TftTouchShield.RED);
        Delay.millis(40);
        Hud.drawHeader();
    }
}
