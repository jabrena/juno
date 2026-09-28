package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

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
@Board(ArduinoUnoQ.class)
public final class RedBaron {
    private static final int FRAME_MILLIS = 40;

    private RedBaron() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * DisplayList.LIST_SIZE];
        int[] ents = new int[Entities.ENTITIES * Entities.E_STRIDE];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            Interludes.opening(lines);
            Interludes.drawTitle(lines, letter);
            Controls.waitForTap();
            Random.seed(Clock.micros());
            Controls.choosePilot();
            Session.newGame();
            boolean alive = true;
            while (alive) {
                alive = runRound(Round.DOGFIGHT, lines, ents)
                        && runRound(Round.GROUND_ATTACK, lines, ents);
                if (alive) {
                    Session.wave = Session.wave + 1;
                }
            }
            Session.endGame();
            Hud.drawHeader();
            DisplayList.clearView();
            Hud.showCentered("GAME OVER", 100, 3, TftTouchShield.RED);
            Delay.millis(3000);
        }
    }

    private static boolean runRound(Round which, short[] lines, int[] ents) {
        startRound(which, ents);
        Hud.drawHeader();
        DisplayList.clearView();
        switch (which) {
            case DOGFIGHT -> DogfightRound.announce();
            default -> GroundAttackRound.announce();
        }
        Delay.millis(1400);
        DisplayList.clearView();

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
            Controls.handleTouch(ents);
            if (Controls.autopilot) {
                AutopilotRB.fly(ents);
            }
            Camera.steer(ents);
            step(ents);
            Combat.resolveBullets(ents);
            SceneRenderer.render(lines, ents);
            if (Session.shotDown) {
                if (!Session.loseAPlane(ents)) {
                    return false;
                }
                next = Clock.millis();
            }
            if (roundOver(ents)) {
                if (which == Round.GROUND_ATTACK) {
                    GroundAttackRound.awardAllTargetsBonus();
                }
                return true;
            }
            if ((Session.frame & 7) == 0) {
                Hud.drawStatus();
            }
        }
    }

    private static void startRound(Round which, int[] ents) {
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

    private static boolean roundOver(int[] ents) {
        return switch (Session.round) {
            case DOGFIGHT -> DogfightRound.isOver(ents);
            default -> GroundAttackRound.isOver(ents);
        };
    }

    private static void step(int[] ents) {
        switch (Session.round) {
            case DOGFIGHT -> DogfightRound.spawn(ents);
            default -> GroundAttackRound.spawn(ents);
        }
        Entities.move(ents);
    }
}
