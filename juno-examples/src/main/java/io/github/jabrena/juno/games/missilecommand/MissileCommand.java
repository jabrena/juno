package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Missile Command on the ELEGOO 2.8" TFT touch screen shield. Enemy warheads rain down on six
 * cities and three missile bases; tap the sky to launch an interceptor from the nearest base that
 * still has ammunition. It detonates where you tapped, and anything its expanding fireball touches
 * is destroyed — including other warheads, whose own explosions can set off a chain reaction.
 *
 * <p>Every base holds {@value Session#BASE_AMMO} interceptors per wave, and a warhead that hits a
 * base empties it. From wave 2, some warheads split into several (MIRVs) halfway down. At the end
 * of a wave, unused interceptors score {@value Session#AMMO_BONUS} points and surviving cities
 * {@value Session#CITY_BONUS} each; every {@value Session#BONUS_CITY_SCORE} points rebuilds a lost
 * city. The game ends when a wave finishes with no city standing.
 *
 * <p>The cover animates two falling warheads, a rising interceptor, chain-reaction fireballs and a
 * zooming title, then lets you choose the pilot: <b>HUMAN</b> to play yourself, or <b>CPU</b> to
 * watch an autopilot that intercepts whichever warhead is closest to the ground, leading its aim a
 * little, hesitating and missing now and then, and never wasting a shot on a warhead another
 * interceptor is already heading for; tap the header during a wave to switch between the two.
 *
 * <p>The package is split by responsibility: {@link Session} owns the rules, score and record
 * layout; {@link Controls} owns touch input and pilot selection; {@link AutopilotMissileCommand} is
 * the CPU player; {@link SceneRenderer} draws the trails, blasts, ground, bases and cities (each
 * trail is one Bresenham line traced a few steps per frame and retraced in the sky's color when the
 * warhead dies, so no stray pixels are left behind; fireballs grow, hold and shrink with filled
 * circles); {@link Hud} and {@link Interludes} own the header and the non-gameplay screens. This
 * class only orchestrates the game loop.
 */
@Board(ArduinoUnoQ.class)
public final class MissileCommand {
    private static final int FRAME_MILLIS = 20;
    private static final int COVER_TIMEOUT_MILLIS = 60_000;

    private MissileCommand() {
    }

    public static void main(String[] args) {
        int[] missiles = new int[Session.MISSILES * Session.T_STRIDE];
        int[] shots = new int[Session.SHOTS * Session.T_STRIDE];
        int[] blasts = new int[Session.BLASTS * Session.B_STRIDE];
        boolean[] alive = new boolean[Session.TARGETS];
        int[] ammo = new int[3];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.cover(alive, ammo);
                tapped = Controls.waitForTap(COVER_TIMEOUT_MILLIS);
            }
            Controls.choosePilot();
            Random.seed(Clock.micros());
            Session.newGame(alive);
            boolean citiesStanding = true;
            while (citiesStanding) {
                Session.startWave(missiles, shots, blasts, alive, ammo);
                Controls.resetForWave();
                TftTouchShield.fillScreen(SceneRenderer.SKY);
                Hud.drawHeader();
                SceneRenderer.drawGround(alive, ammo);
                Interludes.waveCard(Session.wave);
                playWave(missiles, shots, blasts, alive, ammo);
                Interludes.waveCleared(alive, ammo);
                citiesStanding = Session.countCities(alive) > 0;
            }
            Session.finishGame();
            Interludes.gameOver();
            Controls.waitForTap(COVER_TIMEOUT_MILLIS);
        }
    }

    private static void playWave(int[] missiles, int[] shots, int[] blasts, boolean[] alive, int[] ammo) {
        int next = Clock.millis();
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;

            Controls.pollTouch(shots, ammo);
            if (Controls.autopilot) {
                AutopilotMissileCommand.fly(missiles, shots, ammo);
            }
            Session.spawnEnemies(missiles, alive);
            Session.moveShots(shots, blasts);
            Session.moveMissiles(missiles, blasts, alive, ammo);
            Session.updateBlasts(missiles, blasts, alive, ammo);

            if (Session.waveOver(missiles, shots, blasts)) {
                return;
            }
        }
    }
}
