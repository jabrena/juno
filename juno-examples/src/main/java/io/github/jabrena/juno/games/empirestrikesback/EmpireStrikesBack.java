package io.github.jabrena.juno.games.empirestrikesback;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The Empire Strikes Back on the ELEGOO 2.8" TFT touch screen shield, after Atari's 1985 vector
 * arcade game, in landscape. Every wave plays three rounds, a little faster each time:
 *
 * <ol>
 *   <li><b>Probe droids</b>: fly your snowspeeder low over the snowfields of Hoth and destroy the
 *       Imperial probe droids hovering ahead before their shots wear your shields down.</li>
 *   <li><b>The walkers</b>: AT-AT walkers stride towards the rebel base. Their armor stops your
 *       lasers except at the head, which takes three hits; the smaller AT-STs fall to one. Both fire
 *       back. Bringing down every AT-AT pays a bonus.</li>
 *   <li><b>The asteroid field</b>: at the controls of the Millennium Falcon, weave through tumbling
 *       asteroids while TIE fighters close in. An asteroid you fly into takes a shield.</li>
 * </ol>
 *
 * <p>Drag to move the crosshair and tap to fire: the twin lasers converge on the point you tapped.
 * Your craft follows the crosshair, so steering and aiming are one: move away from incoming fire.
 * You start with {@value Session#START_SHIELDS} shields; a hit with none left ends the game, and each
 * completed wave restores one. Finish a round without losing a shield to earn the next letter of
 * <b>JEDI</b>; spell the whole word for a bonus.
 *
 * <p>The game opens like the film: snow falls on Hoth as an Imperial probe streaks down and lands,
 * two AT-ATs stride in from the distance, and the title assembles letter by letter; after each game
 * over it starts again from there. Tap to start, then choose who flies: <b>HUMAN</b> (you) or
 * <b>CPU</b>, an autopilot that lets targets close in, sweeps the crosshair onto them, dodges what
 * is about to hit it and misses now and then, like a person would. Tap the header during the game to switch between the
 * two ({@code CPU} shows in the header).
 *
 * <p>Everything is drawn in vector style from 3D points perspective-projected onto the screen
 * ({@code x' = cx + x·f/z}), with 3D lines clipped at the near plane and 2D lines clipped to the view,
 * using the same double display lists as {@link io.github.jabrena.juno.games.starwars.StarWars}: only lines that changed since the
 * previous frame are erased and drawn again.
 *
 * <p>The package is split by responsibility: {@link Session} holds the game's progress,
 * {@link ProbesRound}, {@link WalkersRound} and {@link AsteroidsRound} each run one round,
 * {@link Entities} moves the world, {@link Combat} resolves the lasers, {@link Controls} and
 * {@link AutopilotESB} fly the craft, {@link SceneRenderer}, {@link Camera} and {@link DisplayList} draw
 * it, and {@link Hud} and {@link Interludes} show the header and the opening. This class runs the game
 * and is the one place that dispatches on the {@link Round}.
 */
@Board(ArduinoUnoQ.class)
public final class EmpireStrikesBack {
    private static final int FRAME_MILLIS = 40;
    private static final int COVER_TIMEOUT_MILLIS = 60_000;

    private EmpireStrikesBack() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * DisplayList.LIST_SIZE];
        int[] ents = new int[Entities.ENTITIES * Entities.E_STRIDE];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.opening(lines, ents);
                Interludes.drawTitle(letter);
                tapped = Controls.waitForTap(COVER_TIMEOUT_MILLIS);
            }
            Random.seed(Clock.micros());
            Controls.choosePilot();
            Session.newGame();
            boolean alive = true;
            while (alive) {
                alive = runRound(Round.PROBES, lines, ents) && runRound(Round.WALKERS, lines, ents)
                        && runRound(Round.ASTEROIDS, lines, ents);
                if (alive) {
                    Session.nextWave();
                }
            }
            Session.endGame();
            Hud.drawHeader();
            DisplayList.clearView();
            Hud.showCentered("GAME OVER", 100, 3, TftTouchShield.RED);
            Delay.millis(3000);
        }
    }

    /** Runs one round frame by frame; returns false when the last shield is lost. */
    private static boolean runRound(Round which, short[] lines, int[] ents) {
        startRound(which, ents);
        Hud.drawHeader();
        DisplayList.clearView();
        switch (which) {
            case PROBES -> ProbesRound.announce();
            case WALKERS -> WalkersRound.announce();
            default -> AsteroidsRound.announce();
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
                AutopilotESB.fly(ents);
            }
            step(ents);
            Combat.resolveShots(ents);
            SceneRenderer.render(lines, ents);
            if (Session.dead) {
                return false;
            }
            if (roundOver(ents)) {
                finishRound();
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
        Session.hitThisRound = false;
        Entities.clear(ents);
        Camera.center();
        Session.travel = 0;
        Session.spawnCountdown = 10;
        switch (which) {
            case PROBES -> ProbesRound.start();
            case WALKERS -> WalkersRound.start();
            default -> AsteroidsRound.start();
        }
    }

    private static boolean roundOver(int[] ents) {
        return switch (Session.round) {
            case PROBES -> ProbesRound.isOver(ents);
            case WALKERS -> WalkersRound.isOver(ents);
            default -> AsteroidsRound.isOver(ents);
        };
    }

    /** Round bonuses and the JEDI letter for a round flown without losing a shield. */
    private static void finishRound() {
        Hud.drawHeader();
        DisplayList.clearView();
        int y = 90;
        if (Session.round == Round.WALKERS && WalkersRound.allAtatsDown()) {
            Session.score = Session.score + Session.ALL_ATATS_BONUS;
            Hud.showCentered("ALL AT-ATS DOWN  +20000", y, 1, SceneRenderer.WALKER_HEAD);
            y = y + 18;
        }
        if (!Session.hitThisRound) {
            Session.jedi = Session.jedi + 1;
            Hud.showCentered("NO SHIELDS LOST", y, 1, TftTouchShield.WHITE);
            Hud.showCentered(Session.jediWord(), y + 14, 2, TftTouchShield.CYAN);
            if (Session.jedi == 4) {
                Session.score = Session.score + Session.JEDI_BONUS;
                Hud.showCentered("JEDI BONUS  +50000", y + 40, 1, TftTouchShield.CYAN);
                Session.jedi = 0;
            }
            y = y + 60;
        }
        Hud.drawHeader();
        if (y > 90) {
            Delay.millis(1600);
        }
    }

    /** One frame of flight: the craft steers, the round spawns what comes next, and all act. */
    private static void step(int[] ents) {
        int speed = Session.flightSpeed();
        Session.travel = Session.travel + speed;
        Camera.steer();
        Session.spawnCountdown = Session.spawnCountdown - 1;
        if (Session.spawnCountdown <= 0) {
            switch (Session.round) {
                case PROBES -> ProbesRound.spawn(ents);
                case WALKERS -> WalkersRound.spawn(ents);
                default -> AsteroidsRound.spawn(ents);
            }
        }
        Entities.move(ents, speed);
    }
}
