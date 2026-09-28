package io.github.jabrena.juno.games.sundance;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Sundance on the ELEGOO 2.8" TFT touch screen shield, after Cinematronics' 1979 vector game.
 * Open matching hatches in two perspective grids to trap the bouncing suns before they collide.
 *
 * <p>The package is split by responsibility: {@link Session} owns the rules and progress,
 * {@link Controls} owns human and CPU play, {@link Grid} projects the board, {@link SceneRenderer}
 * draws the game, {@link DisplayList} updates the vector display, and {@link Hud} and
 * {@link Interludes} own the non-gameplay screens. This class only orchestrates the game loop.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Sundance {
    private static final int FRAME_MILLIS = 40;

    private Sundance() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * DisplayList.LIST_SIZE];
        int[] suns = new int[Session.SUNS * Session.S_STRIDE];
        int[] hatches = new int[Grid.CELLS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        while (true) {
            Interludes.cover(lines, suns, hatches);
            Controls.waitForTap();
            Controls.choosePilot();
            Random.seed(Clock.micros());
            Session.newGame();
            while (Session.lives > 0) {
                if (playRound(lines, suns, hatches)) {
                    Session.advanceRound();
                }
            }
            Session.finishGame();
            Interludes.gameOver(suns);
        }
    }

    /** Plays one round; true when it was cleared, false when time or lives ran out. */
    static boolean playRound(short[] lines, int[] suns, int[] hatches) {
        Session.startRound(suns, hatches);
        Hud.drawHeader(suns);
        Interludes.round(Session.round, Session.quota);

        int next = Clock.millis();
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (Clock.millis() - next > 4 * FRAME_MILLIS) {
                next = Clock.millis();
            }
            Session.frame = Session.frame + 1;
            Controls.update(suns, hatches);
            Session.step(suns, hatches);
            SceneRenderer.render(lines, suns, hatches);
            Hud.drawStatus(suns);
            if (Session.lives == 0 && Session.count(suns, Session.BURST) == 0) {
                Delay.millis(600);
                return false;
            }
            if (Session.timeLeft == 0) {
                Session.lives = Session.lives - 1;
                Interludes.outOfTime();
                return false;
            }
            if (Session.roundCleared(suns)) {
                Interludes.roundCleared(Session.finishRound());
                return true;
            }
        }
    }
}
