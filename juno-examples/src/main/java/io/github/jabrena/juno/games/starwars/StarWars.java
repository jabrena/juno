package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Star Wars on the ELEGOO 2.8" TFT touch screen shield, after Atari's 1983 vector arcade game, in
 * landscape. You fly an X-wing seen from the cockpit through the three phases of the attack on the
 * Death Star, and every wave repeats them a little faster:
 *
 * <ol>
 *   <li><b>Space</b>: TIE fighters swoop in and fire spinning fireballs. Destroy the number shown in
 *       the header. Darth Vader's TIE fighter joins halfway; it cannot be destroyed, and a hit only
 *       sends it spinning away.</li>
 *   <li><b>Surface</b>: laser towers stream past above the Death Star's surface. Shoot their yellow
 *       tops before they fire; destroying every top in the wave pays a bonus.</li>
 *   <li><b>Trench</b>: fly down the trench, where the X-wing follows the crosshair, so you steer as
 *       you aim. Wall turrets fire at you and, from wave 2, catwalks cross the trench: pass above or
 *       below them. At the end, hit the exhaust port. Miss it and you fly the trench again. Hit it
 *       with the only shot you fire in the trench and the Force is with you, for a bonus.</li>
 * </ol>
 *
 * <p>Drag to move the crosshair and tap to fire: the four wing cannons converge on the point you
 * tapped. You start with {@value Session#START_SHIELDS} shields; each fireball or catwalk that hits you takes
 * one, a hit with none left ends the game, and every destroyed Death Star restores one.
 *
 * <p>The game opens like the film: "A long time ago in a galaxy far, far away....", the logo
 * receding into the distance, and a Star Destroyer passing overhead in pursuit of a rebel ship,
 * then the title settles into place; after each game over it starts again from there. Tap to
 * start, then choose who flies the X-wing: <b>HUMAN</b> (you) or <b>CPU</b>, an autopilot that
 * locks on to the nearest target and fires, and in the trench steers above or below the catwalks.
 * Tap the header during the game to switch between the two ({@code CPU} shows in the header).
 *
 * <p>Everything is drawn in vector style from 3D points perspective-projected onto the screen
 * ({@code x' = cx + x·f/z}), with 3D lines clipped at the near plane and 2D lines clipped to the view.
 * Each frame's lines go into one of two display lists; only lines that changed since the previous
 * frame are erased, and only new lines and unchanged lines crossed by an erased one are drawn again,
 * so still parts of the scene (the horizon, far stars, a hovering fighter) cost nothing.
 *
 * <p>The package is split by responsibility: {@link Session} holds the game's progress,
 * {@link SpacePhase}, {@link SurfacePhase} and {@link TrenchPhase} each run one phase, {@link Entities}
 * moves the world, {@link Combat} resolves the lasers, {@link Controls} and {@link AutopilotSW} fly the
 * X-wing, {@link SceneRenderer}, {@link Camera} and {@link DisplayList} draw it, and {@link Hud} and
 * {@link Interludes} show the header and the scenes in between. This class runs the game and is the
 * one place that dispatches on the {@link Phase}.
 */
@Board(ArduinoUnoQ.class)
public final class StarWars {
    private static final int FRAME_MILLIS = 30;

    private StarWars() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * DisplayList.LIST_SIZE];
        int[] ents = new int[Entities.ENTITIES * Entities.E_STRIDE];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            Interludes.opening(lines, letter);
            Interludes.drawTitle(lines);
            Controls.waitForTap();
            Random.seed(Clock.micros());
            Controls.choosePilot();
            Session.newGame();
            boolean alive = true;
            while (alive) {
                alive = playWave(lines, ents);
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

    /** Plays the three phases of one wave; returns false when the last shield is lost. */
    private static boolean playWave(short[] lines, int[] ents) {
        if (!runPhase(Phase.SPACE, lines, ents)) {
            return false;
        }
        Interludes.approachDeathStar();
        if (!runPhase(Phase.SURFACE, lines, ents)) {
            return false;
        }
        SurfacePhase.awardAllTopsBonus();
        while (true) {
            if (!runPhase(Phase.TRENCH, lines, ents)) {
                return false;
            }
            if (TrenchPhase.portDestroyed) {
                break;
            }
            TrenchPhase.announceMiss();
        }
        Interludes.destroyDeathStar();
        return true;
    }

    /** Runs one phase frame by frame; returns false when the X-wing is destroyed. */
    private static boolean runPhase(Phase which, short[] lines, int[] ents) {
        startPhase(which, ents);
        Hud.drawHeader();
        DisplayList.clearView();
        switch (which) {
            case SPACE -> SpacePhase.announce();
            case SURFACE -> SurfacePhase.announce();
            default -> TrenchPhase.announce();
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
                // A slow frame: carry on from now rather than rushing to catch up.
                next = Clock.millis();
            }
            Session.frame = Session.frame + 1;
            Controls.handleTouch(ents);
            if (Controls.autopilot) {
                AutopilotSW.fly(ents);
            }
            step(ents);
            Combat.resolveShots(ents);
            SceneRenderer.render(lines, ents);
            if (Session.dead) {
                return false;
            }
            if (phaseOver(ents)) {
                return true;
            }
            if (Session.phase == Phase.TRENCH && (Session.frame & 7) == 0) {
                Hud.drawStatus();
            }
        }
    }

    private static void startPhase(Phase which, int[] ents) {
        Session.phase = which;
        Session.dead = false;
        Entities.clear(ents);
        Camera.center();
        Session.travel = 0;
        Session.spawnCountdown = 10;
        switch (which) {
            case SPACE -> SpacePhase.start();
            case SURFACE -> SurfacePhase.start();
            default -> TrenchPhase.start();
        }
    }

    private static boolean phaseOver(int[] ents) {
        return switch (Session.phase) {
            case SPACE -> SpacePhase.isOver(ents);
            case SURFACE -> SurfacePhase.isOver(ents);
            default -> TrenchPhase.isOver(ents);
        };
    }

    /** One frame of flight: the world moves nearer, the phase spawns what comes next, and all act. */
    private static void step(int[] ents) {
        int speed = Session.flightSpeed();
        Session.travel = Session.travel + speed;
        if (Session.phase == Phase.TRENCH) {
            Camera.steer();
        }
        switch (Session.phase) {
            case SPACE -> SpacePhase.spawn(ents);
            case SURFACE -> SurfacePhase.spawn(ents);
            default -> TrenchPhase.spawn(ents);
        }
        Entities.move(ents, speed);
    }
}
