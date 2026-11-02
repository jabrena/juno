package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;

/**
 * Red Baron on the ELEGOO 2.8" TFT touch screen shield, after Atari's 1981 vector arcade game.
 * Every wave alternates a dogfight with enemy biplanes and a low-level attack on hangars and flak.
 * Drag the screen to bank and climb, and tap to fire the twin guns.
 *
 * <p>The package is split by responsibility: {@link Session} owns game progress,
 * {@link DogfightRound} and {@link GroundAttackRound} own their round-specific behavior,
 * {@link Entities} moves the packed entity records, {@link Combat} resolves gunfire,
 * {@link Controls} and {@link AutopilotRB} fly the biplane, and {@link Camera},
 * {@link SceneRenderer} and {@link DisplayList} draw the vector scene. This class runs the game and
 * is the one place that dispatches on {@link Round}.
 */
@Board(ArduinoUnoQBoard.class)
public final class RedBaron {
    private static final Clock CLOCK = ArduinoUno.getClock();
    private static final int FRAME_MILLIS = 40;
    private static final int COVER_TIMEOUT_MILLIS = 60_000;

    private RedBaron() {
    }

    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();
        short[] lines = new short[2 * DisplayList.LIST_SIZE];
        int[] ents = new int[Entities.ENTITIES * Entities.E_STRIDE];
        byte[] letter = new byte[1];

        tft.begin();
        tft.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.opening(tft, lines);
                Interludes.drawTitle(tft, lines, letter);
                tapped = Controls.waitForTap(tft, COVER_TIMEOUT_MILLIS);
            }
            Random.seed(CLOCK.micros());
            Controls.choosePilot(tft);
            Session.newGame();
            boolean alive = true;
            while (alive) {
                alive = runRound(tft, Round.DOGFIGHT, lines, ents)
                        && runRound(tft, Round.GROUND_ATTACK, lines, ents);
                if (alive) {
                    Session.wave = Session.wave + 1;
                }
            }
            Session.endGame();
            Hud.drawHeader(tft);
            DisplayList.clearView(tft);
            Hud.showCentered(tft, "GAME OVER", 100, 3, TftTouchShield.RED);
            Delay.millis(3000);
        }
    }

    private static boolean runRound(TftTouchShield tft, Round which, short[] lines, int[] ents) {
        startRound(which, ents);
        Hud.drawHeader(tft);
        DisplayList.clearView(tft);
        switch (which) {
            case DOGFIGHT -> DogfightRound.announce(tft);
            default -> GroundAttackRound.announce(tft);
        }
        Delay.millis(1400);
        DisplayList.clearView(tft);

        int next = CLOCK.millis();
        while (true) {
            while (CLOCK.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (CLOCK.millis() - next > 4 * FRAME_MILLIS) {
                next = CLOCK.millis();
            }
            Session.frame = Session.frame + 1;
            Controls.handleTouch(tft, ents);
            if (Controls.autopilot) {
                AutopilotRB.fly(ents);
            }
            Camera.steer(ents);
            step(ents);
            Combat.resolveBullets(tft, ents);
            SceneRenderer.render(tft, lines, ents);
            if (Session.shotDown) {
                if (!Session.loseAPlane(tft, ents)) {
                    return false;
                }
                next = CLOCK.millis();
            }
            if (roundOver(ents)) {
                if (which == Round.GROUND_ATTACK) {
                    GroundAttackRound.awardAllTargetsBonus(tft);
                }
                return true;
            }
            if ((Session.frame & 7) == 0) {
                Hud.drawStatus(tft);
            }
        }
    }

    static void startRound(Round which, int[] ents) {
        Session.round = which;
        Session.dead = false;
        Session.shotDown = false;
        Camera.startRound(which);
        Session.spawnCountdown = 20;
        Controls.center();
        switch (which) {
            case DOGFIGHT -> DogfightRound.start();
            default -> GroundAttackRound.start();
        }
        Entities.startRound(ents);
    }

    static boolean roundOver(int[] ents) {
        return switch (Session.round) {
            case DOGFIGHT -> DogfightRound.isOver(ents);
            default -> GroundAttackRound.isOver(ents);
        };
    }

    static void step(int[] ents) {
        switch (Session.round) {
            case DOGFIGHT -> DogfightRound.spawn(ents);
            default -> GroundAttackRound.spawn(ents);
        }
        Entities.move(ents);
    }
}
