package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The transitions between phases, in the style of the other arcade games' title and results cards:
 * the cover, the tube emerging at the start of a level with its title card, the results card and
 * the claw's ride down the tube when a level is cleared, the card after a lost claw, and the tube
 * collapsing at game over.
 *
 * <p>The shield bit-bangs its bus, so every animation here is a handful of rings or short lines
 * (see {@link SceneRenderer#sweepRing}), never a full-screen fill per frame.
 */
final class Interludes {
    private static final int CRAWL_STRIDE = 2;
    private static final int CRAWL_MILLIS = 60;
    private static final int LAUNCH_STEPS = 10;
    private static final int LAUNCH_MILLIS = 35;
    private static final int TITLE_MILLIS = 90;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 32;
    private static final int TITLE_WIDTH = 224;
    private static final int TITLE_HEIGHT = 40;
    private static final int RIDE_STEPS = 10;
    private static final int RIDE_MILLIS = 40;

    // The card sits in the middle of the tube, over the far end; the web is redrawn when it goes.
    private static final int CARD_X = 40;
    private static final int CARD_Y = 144;
    private static final int CARD_WIDTH = 160;
    private static final int CARD_HEIGHT = 56;
    private static final int CARD_TITLE_Y = 150;
    private static final int CARD_LINE_Y = 172;
    private static final int CARD_SECOND_LINE_Y = 186;

    private Interludes() {
    }

    /** A self-contained cover: chromatic pulses, a launched claw, and a title zoom. */
    static void cover(int[] tube) {
        Tube.build(0, tube);
        TftTouchShield.fillScreen(Hud.SPACE);
        SceneRenderer.sweepRing(tube, 0, Tube.DEPTH, SceneRenderer.WARP);
        SceneRenderer.sweepRing(tube, Tube.DEPTH, 0, SceneRenderer.FLIPPER_TIPS);
        SceneRenderer.sweepRing(tube, 0, Tube.DEPTH, SceneRenderer.LANE_HIGHLIGHT);
        SceneRenderer.drawWeb(tube, -1);
        launchClaw(tube);
        crawlClaw(tube);
        zoomTitle();
        Hud.showCentered("TAP TO CHOOSE A PILOT", 278, 1, SceneRenderer.WARP);
        Hud.showCentered("Touch the rim to move and fire", 292, 1, TftTouchShield.WHITE);
        Hud.showCentered("Tap the center to superzap", 306, 1, TftTouchShield.WHITE);
    }

    /** A bright lane segment races out of the vanishing point and resolves into the claw. */
    private static void launchClaw(int[] tube) {
        int step = Tube.DEPTH / LAUNCH_STEPS;
        for (int depth = 0; depth <= Tube.DEPTH; depth = depth + step) {
            drawBar(tube, 0, depth, SceneRenderer.CLAW);
            Delay.millis(LAUNCH_MILLIS);
            drawBar(tube, 0, depth, Hud.SPACE);
        }
        SceneRenderer.drawWeb(tube, -1);
    }

    /** The claw hops around the rim in a few big strides, the way it will once you take hold of it. */
    private static void crawlClaw(int[] tube) {
        int lane = 0;
        SceneRenderer.drawClaw(tube, lane, SceneRenderer.CLAW);
        for (int step = 0; step < Tube.LANES; step = step + CRAWL_STRIDE) {
            Delay.millis(CRAWL_MILLIS);
            int next = (lane + CRAWL_STRIDE) % Tube.LANES;
            SceneRenderer.drawClaw(tube, lane, Hud.SPACE);
            SceneRenderer.drawClaw(tube, next, SceneRenderer.CLAW);
            lane = next;
        }
    }

    /** Four centered frames make the logo appear to fly out of the tube toward the player. */
    private static void zoomTitle() {
        titleFrame(1, 55, SceneRenderer.WARP);
        titleFrame(2, 50, SceneRenderer.FLIPPER_TIPS);
        titleFrame(3, 45, SceneRenderer.CLAW);
        titleFrame(4, 40, SceneRenderer.FLIPPER);
    }

    private static void titleFrame(int size, int y, int color) {
        TftTouchShield.fillRect(TITLE_X, TITLE_Y, TITLE_WIDTH, TITLE_HEIGHT, Hud.SPACE);
        Hud.showCentered("TEMPEST", y, size, color);
        Delay.millis(TITLE_MILLIS);
    }

    /** The next level's tube grows out from its vanishing point to the rim, then its web settles. */
    static void emergeFromTube(int[] tube, int highlighted) {
        SceneRenderer.sweepRing(tube, 0, Tube.DEPTH, SceneRenderer.WARP);
        SceneRenderer.drawWeb(tube, highlighted);
    }

    /** The level's title card: its number, the tube's shape, and the goal. */
    static void levelCard(int[] tube, int level, int highlighted) {
        Hud.showCenteredValue("LEVEL ", level, CARD_TITLE_Y, 2, TftTouchShield.GREEN);
        int shape = (level - 1) % 4;
        if (shape == 0) {
            Hud.showCentered("CIRCLE", CARD_LINE_Y, 1, SceneRenderer.WARP);
        } else if (shape == 1) {
            Hud.showCentered("SQUARE", CARD_LINE_Y, 1, SceneRenderer.WARP);
        } else if (shape == 2) {
            Hud.showCentered("STAR", CARD_LINE_Y, 1, SceneRenderer.WARP);
        } else {
            Hud.showCentered("CLOVER", CARD_LINE_Y, 1, SceneRenderer.WARP);
        }
        Hud.showCentered("Shoot the flippers", CARD_SECOND_LINE_Y, 1, TftTouchShield.WHITE);
        Delay.millis(1400);
        clearCard(tube, highlighted);
    }

    /**
     * The results card, then the warp: the claw rides down its lane to the far end while the tube's
     * rings rush in after it, and the screen is left clear for the next level.
     */
    static void levelCleared(int[] tube, int lane, int bonus) {
        Hud.showCentered("CLEARED", CARD_TITLE_Y, 2, TftTouchShield.YELLOW);
        Hud.showCenteredValue("BONUS ", bonus, CARD_LINE_Y + 4, 1, TftTouchShield.WHITE);
        Delay.millis(1200);
        clearCard(tube, lane);
        rideDownTube(tube, lane);
        SceneRenderer.sweepRing(tube, Tube.DEPTH, 0, SceneRenderer.WARP);
        TftTouchShield.fillScreen(Hud.SPACE);
    }

    /** The claw, flattened to a bar across its lane, falls from the rim to the far end. */
    private static void rideDownTube(int[] tube, int lane) {
        SceneRenderer.drawClaw(tube, lane, Hud.SPACE);
        int step = Tube.DEPTH / RIDE_STEPS;
        for (int depth = Tube.DEPTH; depth >= 0; depth = depth - step) {
            drawBar(tube, lane, depth, SceneRenderer.CLAW);
            Delay.millis(RIDE_MILLIS);
            drawBar(tube, lane, depth, Hud.SPACE);
        }
    }

    private static void drawBar(int[] tube, int lane, int depth, int color) {
        Tube.drawLine(Tube.spokeX(tube, lane, depth), Tube.spokeY(tube, lane, depth),
                Tube.spokeX(tube, lane + 1, depth), Tube.spokeY(tube, lane + 1, depth), color);
    }

    /** After a lost claw: how many are left, over the freshly redrawn web. */
    static void clawLost(int[] tube, int lane, int lives) {
        Hud.showCentered("CLAW LOST", CARD_TITLE_Y, 2, SceneRenderer.FLIPPER);
        Hud.showCenteredValue("CLAWS LEFT ", lives, CARD_LINE_Y + 4, 1, TftTouchShield.WHITE);
        Delay.millis(1000);
        clearCard(tube, lane);
        SceneRenderer.drawClaw(tube, lane, SceneRenderer.CLAW);
    }

    /** The tube collapses into its vanishing point, then the final score. */
    static void gameOver(int[] tube) {
        SceneRenderer.sweepRing(tube, Tube.DEPTH, 0, SceneRenderer.FLIPPER);
        TftTouchShield.fillRect(0, Hud.HEADER, Hud.WIDTH, Hud.HEIGHT - Hud.HEADER, Hud.SPACE);
        Hud.drawHeader();
        Hud.showCentered("GAME OVER", 150, 3, SceneRenderer.FLIPPER);
        Hud.showCenteredValue("FINAL SCORE ", Session.score, 190, 1, TftTouchShield.WHITE);
        Hud.showCentered("TAP TO CONTINUE", 214, 1, SceneRenderer.WARP);
        Delay.millis(1500);
    }

    private static void clearCard(int[] tube, int highlighted) {
        TftTouchShield.fillRect(CARD_X, CARD_Y, CARD_WIDTH, CARD_HEIGHT, Hud.SPACE);
        SceneRenderer.drawWeb(tube, highlighted);
    }
}
