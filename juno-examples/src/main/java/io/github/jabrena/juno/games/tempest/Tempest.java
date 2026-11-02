package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;

/**
 * Tempest on the ELEGOO 2.8" TFT touch screen shield: a vector-style tube of {@value Tube#LANES}
 * lanes seen end-on. Your yellow claw rides the outer rim; touch near the rim and it moves around
 * towards your finger, firing down its lane while you hold. Red flippers climb out of the far end,
 * flip between lanes, fire back, and once they reach the rim crawl along it to grab you — shoot them
 * before they do. Tap the center of the tube to fire the Superzapper, which destroys every enemy on
 * screen, once per level. Each level changes the tube's shape (circle, square, star, clover) and
 * adds faster flippers. Three lives.
 *
 * <p>Every phase has its transition (see {@link Interludes}): chromatic rings race through the cover,
 * the claw launches from the tube and circles the rim, and the title zooms into place; each level's
 * tube grows out of its vanishing point under a title card; clearing a level shows a bonus card, then
 * the claw rides down its lane as the rings rush in, like the arcade's warp; the Superzapper sweeps a
 * white ring through the tube; a lost claw shows how many are left; and at game over the tube
 * collapses before the final score. Then choose the pilot:
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
 *
 * <p>Tempest uses only {@link Clock}, {@link Delay}, {@link Random}, and the {@link TftTouchShield}
 * (itself built from {@code Gpio}/{@code Delay}), none of which are board-specific, so it targets both
 * boards the shield fits: pick which one to build for with {@code -Djuno.board=arduino-uno-q} or
 * {@code -Djuno.board=arduino-uno-r4-wifi}.
 */
@Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
public final class Tempest {
    private static final Clock CLOCK = ArduinoUno.getClock();
    private static final int FRAME_MILLIS = 25;

    private Tempest() {
    }

    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();
        int[] tube = new int[Tube.SIZE];
        int[] enemies = new int[Session.ENEMIES * Session.E_STRIDE];
        int[] shots = new int[Session.SHOTS * Session.S_STRIDE];
        int[] bullets = new int[Session.BULLETS * Session.S_STRIDE];

        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            Interludes.cover(tft, tube);
            Controls.waitForTap(tft);
            Controls.choosePilot(tft);
            Random.seed(CLOCK.micros());
            Session.newGame();
            boolean playing = true;
            while (playing) {
                playing = playLevel(tft, tube, enemies, shots, bullets);
            }
            Session.finishGame();
            Interludes.gameOver(tft, tube);
            Controls.waitForTap(tft);
        }
    }

    /** Plays one level; returns false when the last life is lost. */
    private static boolean playLevel(TftTouchShield tft, int[] tube, int[] enemies, int[] shots, int[] bullets) {
        Session.startLevel(enemies, shots, bullets);
        Controls.resetForLevel();
        Tube.build((Session.level - 1) % 4, tube);
        tft.fillScreen(SceneRenderer.SPACE);
        Hud.drawHeader(tft);
        Interludes.emergeFromTube(tft, tube, Session.playerLane);
        SceneRenderer.drawClaw(tft, tube, Session.playerLane, SceneRenderer.CLAW);
        Interludes.levelCard(tft, tube, Session.level, Session.playerLane);

        int next = CLOCK.millis();
        int frame = 0;
        int shownScore = Session.score;
        boolean shownPilot = Controls.autopilot;
        while (true) {
            while (CLOCK.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            frame = frame + 1;

            Session.tickCooldowns();
            Controls.update(tft, tube, shots);
            if (Controls.autopilot) {
                AutopilotTempest.fly(tft, tube, enemies, shots);
            }
            if (Session.consumeZapRequest()) {
                superzap(tft, tube, enemies, bullets);
            }
            Session.moveShots(tft, tube, shots, enemies, bullets);
            Enemies.spawnEnemies(enemies);
            boolean caught = Enemies.moveEnemies(tft, tube, enemies, bullets, frame);
            if (Session.moveBullets(tft, tube, bullets)) {
                caught = true;
            }
            if (Session.score != shownScore || Controls.autopilot != shownPilot) {
                shownScore = Session.score;
                shownPilot = Controls.autopilot;
                Hud.drawHeader(tft);
            }
            if (caught) {
                Session.lives = Session.lives - 1;
                loseLife(tft, tube, enemies, shots, bullets);
                if (Session.lives == 0) {
                    return false;
                }
                next = CLOCK.millis();
            }
            if (Session.levelCleared(enemies)) {
                int bonus = Session.finishLevel();
                Hud.drawHeader(tft);
                SceneRenderer.clearShots(tft, shots, Session.SHOTS);
                SceneRenderer.clearShots(tft, bullets, Session.BULLETS);
                Delay.millis(400);
                Interludes.levelCleared(tft, tube, Session.playerLane, bonus);
                return true;
            }
        }
    }

    private static void superzap(TftTouchShield tft, int[] tube, int[] enemies, int[] bullets) {
        SceneRenderer.zapFlash(tft, tube);
        Session.zapAllEnemies(tft, tube, enemies);
        SceneRenderer.clearShots(tft, bullets, Session.BULLETS);
        SceneRenderer.drawWeb(tft, tube, Session.playerLane);
        Hud.drawHeader(tft);
    }

    private static void loseLife(TftTouchShield tft, int[] tube, int[] enemies, int[] shots, int[] bullets) {
        SceneRenderer.deathFlash(tft, tube, Session.playerLane);
        Delay.millis(500);
        Session.recycleAfterDeath(enemies, shots, bullets);
        tft.fillScreen(SceneRenderer.SPACE);
        SceneRenderer.drawWeb(tft, tube, Session.playerLane);
        Hud.drawHeader(tft);
        if (Session.lives > 0) {
            Interludes.clawLost(tft, tube, Session.playerLane, Session.lives);
        }
    }
}
