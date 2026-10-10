package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.startrek.DisplayList.HEADER;
import static io.github.jabrena.juno.games.startrek.DisplayList.HEIGHT;
import static io.github.jabrena.juno.games.startrek.DisplayList.SPACE;
import static io.github.jabrena.juno.games.startrek.DisplayList.WIDTH;
import static io.github.jabrena.juno.games.startrek.Geometry.SECTOR_FIX;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.STAR;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.STARBASE;

/** The warp opening, the title screen, and the scenes between sectors. */
final class Interludes {
    // The opening: stars (x, y, depth, and the streak drawn last frame) rushing out from the center.
    private static final int INTRO_STARS = 32;
    private static final int S_X = 0;
    private static final int S_Y = 1;
    private static final int S_Z = 2;
    private static final int S_TAIL_X = 3;
    private static final int S_TAIL_Y = 4;
    private static final int S_HEAD_X = 5;
    private static final int S_HEAD_Y = 6;
    private static final int S_STRIDE = 7;
    private static final int INTRO_FRAMES = 70;
    private static final int INTRO_DEPTH = 1024;
    private static final int INTRO_FOCAL = 64;
    private static final String SUBTITLE = "STRATEGIC OPERATIONS SIMULATOR";

    private Interludes() {
    }

    /**
     * The opening: stars rush out from the center of the screen, faster and faster, stretching into
     * streaks as the Enterprise goes to warp, and a white flash as it jumps.
     */
    static void warpIntro(int[] stars) {
        TftTouchShield.fillScreen(SPACE);
        DisplayList.shown = 0;
        for (int i = 0; i < INTRO_STARS; i++) {
            placeStar(stars, i * S_STRIDE, Random.nextInt(64, INTRO_DEPTH));
        }
        for (int f = 0; f < INTRO_FRAMES; f++) {
            int speed = 4 + f * f / 60;
            for (int i = 0; i < INTRO_STARS; i++) {
                int b = i * S_STRIDE;
                if (stars[b + S_HEAD_X] >= 0) {
                    DisplayList.drawLine(stars[b + S_TAIL_X], stars[b + S_TAIL_Y], stars[b + S_HEAD_X],
                            stars[b + S_HEAD_Y], SPACE);
                }
                stars[b + S_Z] = stars[b + S_Z] - speed;
                int z = stars[b + S_Z];
                int headX = WIDTH / 2 + stars[b + S_X] * INTRO_FOCAL / Math.max(z, 1);
                int headY = HEIGHT / 2 + stars[b + S_Y] * INTRO_FOCAL / Math.max(z, 1);
                int tailZ = z + 1 + speed * 2;
                int tailX = WIDTH / 2 + stars[b + S_X] * INTRO_FOCAL / tailZ;
                int tailY = HEIGHT / 2 + stars[b + S_Y] * INTRO_FOCAL / tailZ;
                if (z < 8 || headX < 0 || headX >= WIDTH || headY < 0 || headY >= HEIGHT) {
                    placeStar(stars, b, INTRO_DEPTH);
                    continue;
                }
                int color = z < INTRO_DEPTH / 3 ? TftTouchShield.WHITE : z < INTRO_DEPTH * 2 / 3 ? STARBASE : STAR;
                DisplayList.drawLine(tailX, tailY, headX, headY, color);
                stars[b + S_TAIL_X] = tailX;
                stars[b + S_TAIL_Y] = tailY;
                stars[b + S_HEAD_X] = headX;
                stars[b + S_HEAD_Y] = headY;
            }
            Delay.millis(20);
        }
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        Delay.millis(60);
        TftTouchShield.fillScreen(SPACE);
        Delay.millis(250);
    }

    /** A star far away at a random spot, with no streak on screen yet. */
    private static void placeStar(int[] stars, int b, int depth) {
        stars[b + S_X] = Random.nextInt(-WIDTH * 2, WIDTH * 2);
        stars[b + S_Y] = Random.nextInt(-HEIGHT * 2, HEIGHT * 2);
        stars[b + S_Z] = depth;
        stars[b + S_HEAD_X] = -1;
    }

    /**
     * The title screen: stars, "STAR TREK" zooming in, the subtitle typing out beneath it, then the
     * Enterprise among Klingons.
     */
    static void drawTitle(short[] lines, int[] ents, byte[] letter) {
        for (int i = 0; i < 40; i++) {
            TftTouchShield.drawPixel((i * 797 + 131) % WIDTH, (i * 523 + 37 * i * i) % HEIGHT, STAR);
        }
        for (int size = 1; size <= 4; size++) {
            TftTouchShield.fillRect(0, 36, WIDTH, 40, SPACE);
            Hud.showCentered("STAR TREK", 56 - 4 * size, size, size == 4 ? TftTouchShield.WHITE : STAR);
            Delay.millis(90);
        }
        Delay.millis(120);
        Hud.showCentered("STAR TREK", 40, 4, TftTouchShield.YELLOW);
        Delay.millis(200);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(STARBASE, SPACE);
        TftTouchShield.setCursor((WIDTH - SUBTITLE.length() * 6) / 2, 82);
        for (int i = 0; i < SUBTITLE.length(); i++) {
            letter[0] = (byte) SUBTITLE.charAt(i);
            TftTouchShield.print(letter, 1);
            Delay.millis(35);
        }
        Delay.millis(300);
        Session.sector = 1;
        Entities.clear(ents, Entities.ENTITIES * Entities.E_STRIDE);
        Enterprise.x = SECTOR_FIX / 2;
        Enterprise.y = SECTOR_FIX / 2;
        Enterprise.heading = 30;
        DisplayList.begin();
        DisplayList.setClip(0, HEADER, WIDTH - 1, HEIGHT - 1);
        SceneRenderer.drawKlingonTop(lines, 70, 150, 120);
        SceneRenderer.drawKlingonTop(lines, 250, 160, 240);
        SceneRenderer.drawKlingonTop(lines, 200, 205, 330);
        SceneRenderer.drawEnterprise(lines);
        DisplayList.present(lines);
        Hud.showCentered("Hold the tactical view to steer", 206, 1, TftTouchShield.WHITE);
        Hud.showCentered("Tap to start", 222, 1, TftTouchShield.CYAN);
    }

    /** The bonus for a cleared sector, then streaks rushing out from the center as the Enterprise warps on. */
    static void sectorCleared(int bonus) {
        DisplayList.clearView();
        Hud.showCentered("SECTOR CLEARED", 90, 2, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(118, 122);
        TftTouchShield.print("BONUS ");
        TftTouchShield.print(bonus);
        Hud.showCentered("Warping to the next sector", 142, 1, STARBASE);
        for (int k = 0; k < 12; k++) {
            int r = 10 + k * 10;
            for (int d = 0; d < 8; d++) {
                int dx = Geometry.dirX(d);
                int dy = Geometry.dirY(d);
                DisplayList.drawLine(160 + dx * r / 10, 180 + dy * r / 30, 160 + dx * (r + 8) / 10,
                        180 + dy * (r + 8) / 30, STAR);
            }
            Delay.millis(40);
        }
        Delay.millis(900);
    }

    static void explodeEnterprise() {
        for (int r = 4; r < 60; r = r + 4) {
            TftTouchShield.drawCircle(DisplayList.TACTICAL_X, DisplayList.TACTICAL_Y, r,
                    (r & 4) == 0 ? TftTouchShield.YELLOW : TftTouchShield.RED);
            Delay.millis(30);
        }
        Delay.millis(600);
    }
}
