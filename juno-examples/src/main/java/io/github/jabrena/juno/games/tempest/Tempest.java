package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Tempest on the ELEGOO 2.8" TFT touch screen shield: a vector-style tube of {@value Tube#LANES}
 * lanes seen end-on. Your yellow claw rides the outer rim; touch near the rim and it moves around
 * towards your finger, firing down its lane while you hold. Red flippers climb out of the far end,
 * flip between lanes, fire back, and once they reach the rim crawl along it to grab you — shoot them
 * before they do. Tap the center of the tube to fire the Superzapper, which destroys every enemy on
 * screen, once per level. Each level changes the tube's shape (circle, square, star, clover) and
 * adds faster flippers. Three lives.
 *
 * <p>Every phase has its transition (see {@link Interludes}): the cover pulses a ring out to the rim
 * and hops the claw around it; each level's tube grows out of its vanishing point under a title card;
 * clearing a level shows a bonus card, then the claw rides down its lane as the rings rush in, like
 * the arcade's warp; the Superzapper sweeps a white ring through the tube; a lost claw shows how many
 * are left; and at game over the tube collapses before the final score. Then choose the pilot:
 * <b>HUMAN</b> to play yourself, or <b>CPU</b> to watch an autopilot that lets flippers climb into
 * view before sweeping onto whichever is closest to the rim, occasionally hesitating or misjudging
 * its lane, and firing the Superzapper when swarmed; tap the header during play to switch between the
 * two.
 *
 * <p>The package is split by responsibility: {@link Session} owns the rules, score and record
 * layout; {@link Controls} owns touch input and pilot selection; {@link AutopilotTempest} is the CPU
 * player; {@link Tube} is the tube's perspective geometry and line rasterizer, its rim and near-end
 * points kept in one flat array so every call stays within four arguments; {@link SceneRenderer}
 * draws the web, claw, flippers and shots; {@link Hud} and {@link Interludes} own the header and the
 * non-gameplay screens. This class only orchestrates the game loop.
 */
@Board(ArduinoUnoQ.class)
public final class Tempest {
    private static final int FRAME_MILLIS = 25;

    private Tempest() {
    }

    public static void main(String[] args) {
        int[] tube = new int[Tube.SIZE];
        int[] enemies = new int[Session.ENEMIES * Session.E_STRIDE];
        int[] shots = new int[Session.SHOTS * Session.S_STRIDE];
        int[] bullets = new int[Session.BULLETS * Session.S_STRIDE];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        Interludes.cover(tube);
        Controls.waitForTap();
        Controls.choosePilot();
        Random.seed(Clock.micros());

        while (true) {
            Session.newGame();
            boolean playing = true;
            while (playing) {
                playing = playLevel(tube, enemies, shots, bullets);
            }
            Session.finishGame();
            Interludes.gameOver(tube);
            Controls.waitForTap();
        }
    }

    /** Plays one level; returns false when the last life is lost. */
    private static boolean playLevel(int[] tube, int[] enemies, int[] shots, int[] bullets) {
        Session.startLevel(enemies, shots, bullets);
        Controls.resetForLevel();
        Tube.build((Session.level - 1) % 4, tube);
        TftTouchShield.fillScreen(SceneRenderer.SPACE);
        Hud.drawHeader();
        Interludes.emergeFromTube(tube, Session.playerLane);
        SceneRenderer.drawClaw(tube, Session.playerLane, SceneRenderer.CLAW);
        Interludes.levelCard(tube, Session.level, Session.playerLane);

        int next = Clock.millis();
        int frame = 0;
        int shownScore = Session.score;
        boolean shownPilot = Controls.autopilot;
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            frame = frame + 1;

            Session.tickCooldowns();
            Controls.update(tube, shots);
            if (Controls.autopilot) {
                AutopilotTempest.fly(tube, enemies, shots);
            }
            if (Session.consumeZapRequest()) {
                superzap(tube, enemies, bullets);
            }
            Session.moveShots(tube, shots, enemies, bullets);
            Session.spawnEnemies(enemies);
            boolean caught = Session.moveEnemies(tube, enemies, bullets, frame);
            if (Session.moveBullets(tube, bullets)) {
                caught = true;
            }
            if (Session.score != shownScore || Controls.autopilot != shownPilot) {
                shownScore = Session.score;
                shownPilot = Controls.autopilot;
                Hud.drawHeader();
            }
            if (caught) {
                Session.lives = Session.lives - 1;
                loseLife(tube, enemies, shots, bullets);
                if (Session.lives == 0) {
                    return false;
                }
                next = Clock.millis();
            }
            if (Session.levelCleared(enemies)) {
                int bonus = Session.finishLevel();
                Hud.drawHeader();
                SceneRenderer.clearShots(shots, Session.SHOTS);
                SceneRenderer.clearShots(bullets, Session.BULLETS);
                Delay.millis(400);
                Interludes.levelCleared(tube, Session.playerLane, bonus);
                return true;
            }
        }
    }

    private static void superzap(int[] tube, int[] enemies, int[] bullets) {
        SceneRenderer.zapFlash(tube);
        Session.zapAllEnemies(tube, enemies);
        SceneRenderer.clearShots(bullets, Session.BULLETS);
        SceneRenderer.drawWeb(tube, Session.playerLane);
        Hud.drawHeader();
    }

    private static void loseLife(int[] tube, int[] enemies, int[] shots, int[] bullets) {
        SceneRenderer.deathFlash(tube, Session.playerLane);
        Delay.millis(500);
        Session.recycleAfterDeath(enemies, shots, bullets);
        TftTouchShield.fillScreen(SceneRenderer.SPACE);
        SceneRenderer.drawWeb(tube, Session.playerLane);
        Hud.drawHeader();
        if (Session.lives > 0) {
            Interludes.clawLost(tube, Session.playerLane, Session.lives);
        }
    }
}
