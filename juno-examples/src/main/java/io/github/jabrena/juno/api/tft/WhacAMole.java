package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Whac-a-Mole on the ELEGOO 2.8" TFT touch screen shield: moles pop out of a 3x3 field of holes
 * and you tap them before they duck back down. A brown mole is worth {@value #MOLE_POINTS} point,
 * a golden one — quicker to hide — {@value #GOLD_POINTS}, and tapping a bomb costs
 * {@value #BOMB_PENALTY}. A round lasts {@value #ROUND_SECONDS} seconds (the bar under the header
 * shrinks); as it goes on, more moles are up at once and each stays up for less time.
 *
 * <p>Each hole is redrawn only when its content changes; the moles are made of filled circles and
 * a scanline ellipse, with the front rim of the hole drawn last so they appear to rise out of it.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class WhacAMole {
    private static final int ROUND_SECONDS = 60;
    private static final int ROUND_MILLIS = ROUND_SECONDS * 1000;
    private static final int MOLE_POINTS = 1;
    private static final int GOLD_POINTS = 3;
    private static final int BOMB_PENALTY = 3;
    private static final int HIT_SHOW_MILLIS = 350;

    private static final int HOLES = 9;
    private static final int EMPTY = 0;
    private static final int MOLE = 1;
    private static final int GOLD = 2;
    private static final int BOMB = 3;
    private static final int WHACKED = 4;
    private static final int EXPLODED = 5;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEIGHT = 320;
    private static final int HEADER = 30;
    private static final int BAR_Y = HEADER;
    private static final int BAR_HEIGHT = 5;
    private static final int FIELD_TOP = BAR_Y + BAR_HEIGHT;
    private static final int CELL_WIDTH = WIDTH / 3;
    private static final int CELL_HEIGHT = (HEIGHT - FIELD_TOP) / 3;
    private static final int HOLE_RX = 32;
    private static final int HOLE_RY = 11;
    private static final int HEAD_RADIUS = 20;
    private static final int HIT_RADIUS = 38;

    private static final int GRASS = 0x2C84;
    private static final int HOLE = 0x2000;
    private static final int HOLE_RIM = 0x5140;
    private static final int MOLE_COLOR = 0x8A22;
    private static final int GOLD_COLOR = 0xFE60;
    private static final int SNOUT = 0xFD55;
    private static final int NOSE = 0xF8B2;
    private static final int BOMB_COLOR = 0x2104;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static int score;
    private static int best;
    private static int roundStart;
    private static int shownBar;
    private static boolean touching;
    private static int releaseMisses;

    private WhacAMole() {
    }

    public static void main(String[] args) {
        int[] content = new int[HOLES];
        int[] until = new int[HOLES];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        drawField();
        drawHeader();
        showBanner("Whac-a-Mole", "Tap to start");
        waitForTap();
        Random.seed(Clock.micros());

        while (true) {
            playRound(content, until);
            if (score > best) {
                best = score;
            }
            drawHeader();
            showBanner("Time up!", "Tap to play again");
            showScoreLine();
            Delay.millis(800);
            waitForTap();
        }
    }

    // ---- Game flow ----

    private static void playRound(int[] content, int[] until) {
        score = 0;
        drawField();
        for (int hole = 0; hole < HOLES; hole++) {
            content[hole] = EMPTY;
            until[hole] = 0;
        }
        shownBar = -1;
        drawHeader();
        roundStart = Clock.millis();
        int nextSpawn = roundStart + 600;
        while (true) {
            int now = Clock.millis();
            int elapsed = now - roundStart;
            if (elapsed >= ROUND_MILLIS) {
                break;
            }
            drawBar(elapsed);

            // Moles and bombs whose time is up duck back down.
            for (int hole = 0; hole < HOLES; hole++) {
                if (content[hole] != EMPTY && now - until[hole] >= 0) {
                    content[hole] = EMPTY;
                    drawHole(hole, EMPTY);
                }
            }

            if (now - nextSpawn >= 0) {
                if (countUp(content) < maxUp(elapsed)) {
                    spawn(content, until, now, elapsed);
                }
                nextSpawn = now + Random.nextInt(250, 700) - elapsed / 150;
            }

            int hole = pollTap();
            if (hole >= 0) {
                whack(content, until, hole, now);
            }
            Delay.millis(5);
        }
        for (int hole = 0; hole < HOLES; hole++) {
            if (content[hole] != EMPTY) {
                content[hole] = EMPTY;
                drawHole(hole, EMPTY);
            }
        }
        drawBar(ROUND_MILLIS);
    }

    private static void spawn(int[] content, int[] until, int now, int elapsed) {
        int free = HOLES - countUp(content);
        int pick = Random.nextInt(free);
        for (int hole = 0; hole < HOLES; hole++) {
            if (content[hole] == EMPTY) {
                if (pick == 0) {
                    int kind = MOLE;
                    int roll = Random.nextInt(100);
                    if (roll < 12) {
                        kind = BOMB;
                    } else if (roll < 22) {
                        kind = GOLD;
                    }
                    // Up-time shrinks from 1.3 s to 0.6 s over the round; gold moles are quicker.
                    int upTime = 1300 - elapsed * 700 / ROUND_MILLIS;
                    if (kind == GOLD) {
                        upTime = upTime * 2 / 3;
                    } else if (kind == BOMB) {
                        upTime = upTime + 400;
                    }
                    content[hole] = kind;
                    until[hole] = now + upTime;
                    drawHole(hole, kind);
                    return;
                }
                pick = pick - 1;
            }
        }
    }

    private static void whack(int[] content, int[] until, int hole, int now) {
        int kind = content[hole];
        if (kind == MOLE || kind == GOLD) {
            if (kind == GOLD) {
                score = score + GOLD_POINTS;
            } else {
                score = score + MOLE_POINTS;
            }
            content[hole] = WHACKED;
            until[hole] = now + HIT_SHOW_MILLIS;
            drawHole(hole, WHACKED);
            drawHeader();
        } else if (kind == BOMB) {
            score = Math.max(0, score - BOMB_PENALTY);
            content[hole] = EXPLODED;
            until[hole] = now + HIT_SHOW_MILLIS * 2;
            drawHole(hole, EXPLODED);
            drawHeader();
        }
    }

    private static int countUp(int[] content) {
        int count = 0;
        for (int hole = 0; hole < HOLES; hole++) {
            if (content[hole] != EMPTY) {
                count = count + 1;
            }
        }
        return count;
    }

    /** One mole at a time at first, up to three in the last third of the round. */
    private static int maxUp(int elapsed) {
        return 1 + elapsed * 3 / ROUND_MILLIS;
    }

    // ---- Input ----

    /** Returns the hole of a new press, or -1; holding a touch does not repeat. */
    private static int pollTap() {
        if (!TftTouchShield.readTouch()) {
            releaseMisses = releaseMisses + 1;
            if (releaseMisses >= 3) {
                touching = false;
            }
            return -1;
        }
        releaseMisses = 0;
        if (touching) {
            return -1;
        }
        touching = true;
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        for (int hole = 0; hole < HOLES; hole++) {
            int dx = x - holeX(hole);
            int dy = y - (holeY(hole) - 4);
            if (dx * dx + dy * dy <= HIT_RADIUS * HIT_RADIUS) {
                return hole;
            }
        }
        return -1;
    }

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
        touching = false;
    }

    // ---- Drawing ----

    private static int holeX(int hole) {
        return (hole % 3) * CELL_WIDTH + CELL_WIDTH / 2;
    }

    /** The y of the hole's opening; a mole's head sits just above it. */
    private static int holeY(int hole) {
        return FIELD_TOP + (hole / 3) * CELL_HEIGHT + CELL_HEIGHT / 2 + 14;
    }

    private static void drawField() {
        TftTouchShield.fillRect(0, FIELD_TOP, WIDTH, HEIGHT - FIELD_TOP, GRASS);
        for (int hole = 0; hole < HOLES; hole++) {
            drawHole(hole, EMPTY);
        }
    }

    private static void drawHole(int hole, int kind) {
        int cx = holeX(hole);
        int cy = holeY(hole);
        // Clear everything a mole, a bomb's fuse or the "POW" can cover above the hole, then the hole.
        int clearTop = Math.max(FIELD_TOP, cy - 2 * HEAD_RADIUS - 22);
        TftTouchShield.fillRect(cx - HOLE_RX - 2, clearTop, 2 * HOLE_RX + 4, cy - clearTop, GRASS);
        fillEllipse(cx, cy, HOLE_RX + 3, HOLE_RY + 3, HOLE_RIM, false);
        fillEllipse(cx, cy, HOLE_RX, HOLE_RY, HOLE, false);
        if (kind == EMPTY) {
            return;
        }
        int headY = cy - HEAD_RADIUS - 6;
        if (kind == BOMB || kind == EXPLODED) {
            drawBomb(cx, headY, kind == EXPLODED);
        } else {
            int fur = MOLE_COLOR;
            if (kind == GOLD) {
                fur = GOLD_COLOR;
            }
            TftTouchShield.fillRect(cx - HEAD_RADIUS, headY, 2 * HEAD_RADIUS + 1, cy - headY, fur);
            TftTouchShield.fillCircle(cx, headY, HEAD_RADIUS, fur);
            drawFace(cx, headY, kind == WHACKED);
        }
        // The front rim goes over the mole's body so it seems to rise out of the hole.
        fillEllipse(cx, cy, HOLE_RX + 3, HOLE_RY + 3, HOLE_RIM, true);
    }

    private static void drawFace(int cx, int cy, boolean dizzy) {
        if (dizzy) {
            drawCross(cx - 8, cy - 5, TftTouchShield.BLACK);
            drawCross(cx + 8, cy - 5, TftTouchShield.BLACK);
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.YELLOW, TftTouchShield.RED);
            TftTouchShield.setCursor(cx - 11, cy - HEAD_RADIUS - 6);
            TftTouchShield.print("POW");
        } else {
            TftTouchShield.fillCircle(cx - 8, cy - 5, 4, TftTouchShield.BLACK);
            TftTouchShield.fillCircle(cx + 8, cy - 5, 4, TftTouchShield.BLACK);
            TftTouchShield.fillRect(cx - 9, cy - 7, 2, 2, TftTouchShield.WHITE);
            TftTouchShield.fillRect(cx + 7, cy - 7, 2, 2, TftTouchShield.WHITE);
        }
        TftTouchShield.fillCircle(cx, cy + 7, 8, SNOUT);
        TftTouchShield.fillCircle(cx, cy + 3, 3, NOSE);
        TftTouchShield.fillRect(cx - 3, cy + 12, 3, 4, TftTouchShield.WHITE);
        TftTouchShield.fillRect(cx + 1, cy + 12, 3, 4, TftTouchShield.WHITE);
    }

    private static void drawCross(int cx, int cy, int color) {
        for (int i = -3; i <= 3; i++) {
            TftTouchShield.fillRect(cx + i, cy + i, 2, 2, color);
            TftTouchShield.fillRect(cx + i, cy - i, 2, 2, color);
        }
    }

    private static void drawBomb(int cx, int cy, boolean exploded) {
        if (exploded) {
            TftTouchShield.fillCircle(cx, cy, HEAD_RADIUS + 4, TftTouchShield.ORANGE);
            TftTouchShield.fillCircle(cx, cy, HEAD_RADIUS - 6, TftTouchShield.YELLOW);
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.RED, TftTouchShield.YELLOW);
            TftTouchShield.setCursor(cx - 5, cy - 3);
            TftTouchShield.print("-");
            TftTouchShield.print(BOMB_PENALTY);
            return;
        }
        TftTouchShield.fillCircle(cx, cy + 2, HEAD_RADIUS - 2, BOMB_COLOR);
        TftTouchShield.fillCircle(cx - 7, cy - 5, 4, TftTouchShield.GRAY);
        TftTouchShield.fillRect(cx - 4, cy - HEAD_RADIUS - 2, 8, 6, TftTouchShield.GRAY);
        TftTouchShield.fillRect(cx - 1, cy - HEAD_RADIUS - 10, 2, 8, 0xC618);
        TftTouchShield.fillCircle(cx, cy - HEAD_RADIUS - 12, 3, TftTouchShield.RED);
        TftTouchShield.fillCircle(cx, cy - HEAD_RADIUS - 12, 1, TftTouchShield.YELLOW);
    }

    /** Fills an ellipse by scanlines; with {@code lowerHalf} only the half below its center. */
    private static void fillEllipse(int cx, int cy, int rx, int ry, int color, boolean lowerHalf) {
        int top = -ry;
        if (lowerHalf) {
            top = 1;
        }
        for (int dy = top; dy <= ry; dy++) {
            // Half-width from x^2/rx^2 + y^2/ry^2 <= 1.
            int span = isqrt(rx * rx * (ry * ry - dy * dy) / (ry * ry));
            TftTouchShield.fillRect(cx - span, cy + dy, 2 * span + 1, 1, color);
        }
    }

    private static int isqrt(int value) {
        if (value <= 0) {
            return 0;
        }
        int root = (int) Math.sqrt((double) value);
        while (root * root > value) {
            root = root - 1;
        }
        while ((root + 1) * (root + 1) <= value) {
            root = root + 1;
        }
        return root;
    }

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 8);
        TftTouchShield.print("Score ");
        TftTouchShield.print(score);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(144, 8);
        TftTouchShield.print("Best ");
        TftTouchShield.print(best);
    }

    /** The time bar under the header, shrinking from full to empty over the round. */
    private static void drawBar(int elapsed) {
        int length = WIDTH - Math.min(elapsed, ROUND_MILLIS) * WIDTH / ROUND_MILLIS;
        if (length == shownBar) {
            return;
        }
        int color = TftTouchShield.GREEN;
        if (length < WIDTH / 6) {
            color = TftTouchShield.RED;
        } else if (length < WIDTH / 3) {
            color = TftTouchShield.YELLOW;
        }
        if (shownBar < 0 || color != barColor(shownBar)) {
            TftTouchShield.fillRect(0, BAR_Y, length, BAR_HEIGHT, color);
        }
        TftTouchShield.fillRect(length, BAR_Y, WIDTH - length, BAR_HEIGHT, TftTouchShield.BLACK);
        shownBar = length;
    }

    private static int barColor(int length) {
        if (length < WIDTH / 6) {
            return TftTouchShield.RED;
        }
        if (length < WIDTH / 3) {
            return TftTouchShield.YELLOW;
        }
        return TftTouchShield.GREEN;
    }

    private static void showBanner(String title, String hint) {
        int top = FIELD_TOP + CELL_HEIGHT - 26;
        TftTouchShield.fillRect(10, top, WIDTH - 20, 76, HEADER_BACKGROUND);
        TftTouchShield.drawRect(10, top, WIDTH - 20, 76, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor((WIDTH - title.length() * 12) / 2, top + 10);
        TftTouchShield.print(title);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor((WIDTH - hint.length() * 6) / 2, top + 60);
        TftTouchShield.print(hint);
    }

    private static void showScoreLine() {
        int top = FIELD_TOP + CELL_HEIGHT - 26;
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(60, top + 34);
        TftTouchShield.print("Score ");
        TftTouchShield.print(score);
    }
}
