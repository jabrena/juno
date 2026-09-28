package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Star Trek on the ELEGOO 2.8" TFT touch screen shield, after Sega's 1982 color vector arcade game,
 * in landscape. You command the Enterprise through sector after sector: destroy every Klingon
 * battlecruiser in the sector to move on to the next.
 *
 * <p>The big tactical view on the left shows the sector from above, with the Enterprise in the
 * middle; the small window on the right looks ahead from the bridge. Press and hold anywhere in the
 * tactical view: the Enterprise turns towards your finger and moves ahead on impulse power. The
 * buttons on the right fire the <b>phasers</b> straight ahead (they need a moment to recharge between
 * shots), launch a <b>photon torpedo</b> whose blast destroys everything near it, and <b>warp</b>
 * you far ahead, out of trouble. Photon torpedoes and warp jumps are limited; fly into the starbase
 * to dock, which also restores your shields.
 *
 * <p>The game opens with a starfield rushing past as the Enterprise goes to warp, then the title
 * zooms in and <i>Strategic Operations Simulator</i> types out beneath it; after each game over it
 * starts again from there. Tap to start, then choose who commands the Enterprise: <b>HUMAN</b>
 * (you) or <b>CPU</b>, an autopilot that hunts the nearest enemy, docks when its shields run low
 * and uses every weapon. Tap the header during the game to switch between the two ({@code CPU}
 * shows in the header).
 *
 * <p>Klingons circle you, and sometimes your starbase, firing torpedoes, and a starbase that takes
 * too many hits is lost. From sector 2, anti-matter saucers home in on the Enterprise and drain its
 * shields if they touch it; every fourth sector the space probe Nomad roams the sector laying mines.
 * Every hit takes some of your shields; when they are gone, so is the Enterprise.
 *
 * <p>Everything is drawn in vector style with the double display lists of
 * {@link io.github.jabrena.juno.games.starwars.StarWars}, clipped to whichever view is being drawn:
 * only lines that changed since the previous frame are erased and drawn again. Positions are fixed
 * point (1/16 unit) in a sector that wraps around at the edges.
 *
 * <p>The package is split by responsibility: {@link Session} owns game progress, {@link Sector}
 * sets up and scores each sector, {@link Enterprise} flies the ship, {@link Entities} moves the
 * packed entity records, {@link Enemies} steers the opposition, {@link Combat} resolves weapons,
 * {@link Controls} and {@link AutopilotST} take the helm, and {@link SceneRenderer},
 * {@link BridgeView} and {@link DisplayList} draw the vector views. This class runs the game.
 */
@Board(ArduinoUnoQ.class)
public final class StarTrek {
    private static final int FRAME_MILLIS = 40;

    private StarTrek() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * DisplayList.LIST_SIZE];
        int[] ents = new int[Entities.ENTITIES * Entities.E_STRIDE];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            Interludes.warpIntro(ents);
            Interludes.drawTitle(lines, ents, letter);
            Controls.waitForTap();
            Random.seed(Clock.micros());
            Controls.choosePilot();
            Session.newGame();
            while (playSector(lines, ents)) {
                Session.sector = Session.sector + 1;
            }
            Session.endGame();
            Hud.drawHeader(ents);
            DisplayList.clearView();
            Hud.showCentered("GAME OVER", 100, 3, TftTouchShield.RED);
            Delay.millis(3000);
        }
    }

    /** Plays one sector; returns false when the Enterprise is destroyed. */
    private static boolean playSector(short[] lines, int[] ents) {
        Sector.start(ents);
        Hud.drawHeader(ents);
        DisplayList.clearView();
        Sector.announce();
        Delay.millis(1500);
        DisplayList.clearView();
        Hud.drawPanel();

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
                AutopilotST.fly(ents);
            }
            step(ents);
            SceneRenderer.render(lines, ents);
            if (Session.dead) {
                Interludes.explodeEnterprise();
                return false;
            }
            if (Sector.isCleared(ents)) {
                Sector.finish();
                return true;
            }
            if (Hud.headerFlashed) {
                Hud.drawHeader(ents);
            } else {
                Hud.drawStatus(ents);
            }
        }
    }

    private static void step(int[] ents) {
        Enterprise.steer();
        Enterprise.move();
        Combat.recharge();
        Entities.move(ents);
    }
}
