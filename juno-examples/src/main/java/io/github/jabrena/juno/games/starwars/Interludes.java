package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The scenes between the flying: the film's opening and the title, the approach to the Death Star
 * and its destruction, which pays the wave's bonuses.
 */
final class Interludes {
    private static final int OPENING = 0x4D7F;
    private static final String OPENING_LINE_1 = "A long time ago in a galaxy";
    private static final String OPENING_LINE_2 = "far, far away....";

    // The opening scene: the Star Destroyer passing over the planet after the rebel ship.
    private static final int SCENE_FRAMES = 70;
    private static final int PLANET_Y = 640;
    private static final int PLANET_RADIUS = 470;
    private static final int DESTROYER = 0xBDF7;
    private static final int RUNNER = TftTouchShield.WHITE;

    private Interludes() {
    }

    /**
     * The opening, after the film: "A long time ago in a galaxy far, far away....", the STAR WARS
     * logo receding into the distance until it is gone, then the first shot of the film.
     */
    static void opening(short[] lines, byte[] letter) {
        TftTouchShield.fillScreen(DisplayList.SPACE);
        DisplayList.forget();
        typeCentered(OPENING_LINE_1, 104, letter);
        typeCentered(OPENING_LINE_2, 120, letter);
        Delay.millis(900);
        TftTouchShield.fillScreen(DisplayList.SPACE);
        Delay.millis(300);
        for (int size = 5; size >= 1; size--) {
            TftTouchShield.fillRect(0, 60, DisplayList.WIDTH, 60, DisplayList.SPACE);
            Hud.showCentered("STAR WARS", 90 - 4 * size, size, TftTouchShield.YELLOW);
            Delay.millis(size == 5 ? 500 : 260);
        }
        TftTouchShield.fillRect(0, 60, DisplayList.WIDTH, 60, DisplayList.SPACE);
        Delay.millis(300);
        flyover(lines);
        TftTouchShield.fillScreen(DisplayList.SPACE);
        DisplayList.forget();
        Delay.millis(250);
    }

    /**
     * The first shot of the film: above a planet's horizon a small rebel ship flees into the
     * distance, and the wedge of an Imperial Star Destroyer slides in overhead after it, firing.
     */
    private static void flyover(short[] lines) {
        TftTouchShield.fillScreen(DisplayList.SPACE);
        DisplayList.forget();
        for (int f = 0; f < SCENE_FRAMES; f++) {
            DisplayList.begin();
            SceneRenderer.drawStars(lines);
            drawHorizon(lines);
            // The rebel ship, running for the horizon and getting smaller.
            int runnerX = Camera.CENTER_X + 30 - f / 3;
            int runnerY = 80 + f * 3 / 2;
            int r = Math.max(1, 6 - f / 14);
            DisplayList.addLine(lines, runnerX - r, runnerY, runnerX + r, runnerY, RUNNER);
            DisplayList.addLine(lines, runnerX, runnerY - r, runnerX, runnerY + r / 2, RUNNER);
            // The Star Destroyer: a wedge whose point slides down from the top, its hull behind it.
            int apexX = Camera.CENTER_X + 10 - f / 4;
            int apexY = DisplayList.HEADER - 10 + f * 11 / 5;
            int span = 40 + f * 5;
            int back = DisplayList.HEADER - 40;
            DisplayList.addLine(lines, apexX, apexY, apexX - span, back, DESTROYER);
            DisplayList.addLine(lines, apexX, apexY, apexX + span, back, DESTROYER);
            DisplayList.addLine(lines, apexX, apexY, apexX, back, DESTROYER);
            for (int k = 1; k <= 3; k++) {
                int y = apexY - (apexY - back) * k / 4;
                int half = span * k / 4;
                DisplayList.addLine(lines, apexX - half, y, apexX + half, y, SceneRenderer.STRUCTURE);
            }
            // Laser bolts from its forward guns at the fleeing ship.
            if (f > 10 && f % 6 < 3) {
                int step = f % 6;
                int fromX = apexX + (runnerX - apexX) * (step * 30 + 10) / 100;
                int fromY = apexY + (runnerY - apexY) * (step * 30 + 10) / 100;
                int toX = apexX + (runnerX - apexX) * (step * 30 + 25) / 100;
                int toY = apexY + (runnerY - apexY) * (step * 30 + 25) / 100;
                DisplayList.addLine(lines, fromX, fromY, toX, toY, SceneRenderer.LASER);
            }
            DisplayList.present(lines);
            Delay.millis(30);
        }
        Delay.millis(400);
    }

    /** The planet's horizon, an arc across the bottom of the screen. */
    private static void drawHorizon(short[] lines) {
        int previousX = 0;
        int previousY = 0;
        for (int a = -24; a <= 24; a = a + 4) {
            float radians = (float) Math.toRadians(a);
            int x = Camera.CENTER_X + Math.round((float) Math.sin(radians) * PLANET_RADIUS);
            int y = PLANET_Y - Math.round((float) Math.cos(radians) * PLANET_RADIUS);
            if (a > -24) {
                DisplayList.addLine(lines, previousX, previousY, x, y, OPENING);
            }
            previousX = x;
            previousY = y;
        }
    }

    /** Types a line of the opening letter by letter, centered. */
    private static void typeCentered(String text, int y, byte[] letter) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(OPENING, DisplayList.SPACE);
        TftTouchShield.setCursor((DisplayList.WIDTH - text.length() * 6) / 2, y);
        for (int i = 0; i < text.length(); i++) {
            letter[0] = (byte) text.charAt(i);
            TftTouchShield.print(letter, 1);
            Delay.millis(35);
        }
    }

    /**
     * The title screen: yellow frames close in on "STAR WARS" as the logo settles into place, then
     * the TIE fighters.
     */
    static void drawTitle(short[] lines) {
        int width = DisplayList.WIDTH;
        int textLeft = (width - 9 * 24) / 2;
        for (int k = 10; k >= 0; k--) {
            int left = textLeft - 8 - k * (textLeft - 8) / 10;
            int top = 26 - k * 26 / 10;
            int right = width - 1 - left;
            int bottom = 74 + k * (DisplayList.HEIGHT - 75) / 10;
            TftTouchShield.drawRect(left, top, right - left + 1, bottom - top + 1, TftTouchShield.YELLOW);
            Delay.millis(45);
            TftTouchShield.drawRect(left, top, right - left + 1, bottom - top + 1, DisplayList.SPACE);
        }
        Hud.showCentered("STAR WARS", 34, 4, TftTouchShield.WHITE);
        Delay.millis(120);
        Hud.showCentered("STAR WARS", 34, 4, TftTouchShield.YELLOW);
        Delay.millis(300);
        Session.phase = Phase.SPACE;
        DisplayList.begin();
        SceneRenderer.drawStars(lines);
        SceneRenderer.drawTie(lines, Camera.projectX(0, 420), Camera.projectY(-10, 420), 420, false);
        SceneRenderer.drawTie(lines, Camera.projectX(-520, 1500), Camera.projectY(160, 1500), 1500, false);
        SceneRenderer.drawTie(lines, Camera.projectX(560, 1800), Camera.projectY(120, 1800), 1800, true);
        DisplayList.present(lines);
        Hud.showCentered("Drag to aim, tap to fire", 198, 1, TftTouchShield.WHITE);
        Hud.showCentered("Tap to start", 214, 1, TftTouchShield.CYAN);
    }

    private static void drawDeathStar(int color) {
        TftTouchShield.drawCircle(Camera.CENTER_X, Camera.CENTER_Y, 62, color);
        TftTouchShield.drawHorizontalLine(Camera.CENTER_X - 62, Camera.CENTER_Y + 4, 124, color);
        TftTouchShield.drawCircle(Camera.CENTER_X - 24, Camera.CENTER_Y - 24, 14, color);
    }

    static void approachDeathStar() {
        DisplayList.clearView();
        for (int step = 0; step < 3; step++) {
            drawDeathStar(TftTouchShield.GRAY);
            Delay.millis(250);
            drawDeathStar(TftTouchShield.WHITE);
            Delay.millis(250);
        }
        Hud.showCentered("APPROACHING THE DEATH STAR", 212, 1, TftTouchShield.WHITE);
        Delay.millis(900);
    }

    /** The Death Star explodes; the shields left and the Force pay bonuses, and a shield is restored. */
    static void destroyDeathStar() {
        DisplayList.clearView();
        drawDeathStar(TftTouchShield.GRAY);
        Delay.millis(300);
        for (int r = 4; r < 150; r = r + 5) {
            int color = TftTouchShield.WHITE;
            if (r % 15 == 9) {
                color = TftTouchShield.YELLOW;
            } else if (r % 15 == 14) {
                color = TftTouchShield.ORANGE;
            }
            TftTouchShield.drawCircle(Camera.CENTER_X, Camera.CENTER_Y, r, color);
            Delay.millis(20);
        }
        int shieldBonus = Session.shields * Session.SHIELD_BONUS;
        boolean force = TrenchPhase.withTheForce();
        Session.score = Session.score + shieldBonus;
        if (force) {
            Session.score = Session.score + Session.FORCE_BONUS;
        }
        Session.shields = Math.min(Session.shields + 1, Session.MAX_SHIELDS);
        Hud.drawHeader();
        DisplayList.clearView();
        Hud.showCentered("DEATH STAR DESTROYED", 80, 2, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, DisplayList.SPACE);
        TftTouchShield.setCursor(106, 120);
        TftTouchShield.print("SHIELD BONUS ");
        TftTouchShield.print(shieldBonus);
        if (force) {
            Hud.showCentered("THE FORCE IS WITH YOU  +50000", 140, 1, TftTouchShield.CYAN);
        }
        Delay.millis(2500);
    }
}
