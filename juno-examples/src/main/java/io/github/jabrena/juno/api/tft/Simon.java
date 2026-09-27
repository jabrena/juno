package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * The Simon memory game for the ELEGOO 2.8" TFT touch screen shield: four colored pads around a
 * central hub, like the classic toy. The board lights a sequence of pads; repeat it by tapping
 * them. Each round adds one step and plays a little faster. A wrong pad, or no tap within five
 * seconds, ends the game.
 *
 * <p>Tap the hub to start. The hub shows the current score and the header the best score since
 * power-up. The shield has no speaker, so this version is lights only.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Simon {
    private static final int MAX_STEPS = 100;
    private static final int INPUT_TIMEOUT_MILLIS = 5000;

    // Pads, clockwise from the top left.
    private static final int GREEN = 0;
    private static final int RED = 1;
    private static final int BLUE = 3;

    private static final int GREEN_DIM = 0x0360;
    private static final int GREEN_LIT = 0x5FEB;
    private static final int RED_DIM = 0x8000;
    private static final int RED_LIT = 0xFA8A;
    private static final int YELLOW_DIM = 0x8C00;
    private static final int YELLOW_LIT = 0xFFED;
    private static final int BLUE_DIM = 0x0011;
    private static final int BLUE_LIT = 0x6D5F;
    private static final int HEADER_BACKGROUND = 0x2945;

    // Layout (portrait, 240x320): a ring of four quarter pads around a hub.
    private static final int HEADER_HEIGHT = 40;
    private static final int CENTER_X = 120;
    private static final int CENTER_Y = 178;
    private static final int OUTER_RADIUS = 112;
    private static final int HUB_RADIUS = 46;
    private static final int GAP = 5;

    // Game states.
    private static final int WAITING = 0;
    private static final int SHOWING = 1;
    private static final int LISTENING = 2;

    private static int state;
    private static int length;
    private static int best;

    private Simon() {
    }

    public static void main(String[] args) {
        byte[] sequence = new byte[MAX_STEPS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        for (int pad = 0; pad < 4; pad++) {
            drawPad(pad, false);
        }
        state = WAITING;
        showStatus("Tap center", TftTouchShield.WHITE);
        drawHub("TAP");

        while (true) {
            if (state == WAITING) {
                waitForHubTap();
                startGame(sequence);
            }
            if (state == SHOWING) {
                showStatus("Watch...", TftTouchShield.YELLOW);
                Delay.millis(500);
                playSequence(sequence);
                state = LISTENING;
                showStatus("Your turn", TftTouchShield.WHITE);
            }
            if (state == LISTENING) {
                if (repeatSequence(sequence)) {
                    drawScore(length);
                    if (length == MAX_STEPS) {
                        gameOver("You win!");
                    } else {
                        sequence[length] = (byte) Random.nextInt(4);
                        length = length + 1;
                        state = SHOWING;
                        Delay.millis(400);
                    }
                } else {
                    gameOver("Game over");
                }
            }
        }
    }

    // ---- Game flow ----

    private static void startGame(byte[] sequence) {
        Random.seed(Clock.micros());
        sequence[0] = (byte) Random.nextInt(4);
        length = 1;
        drawScore(0);
        state = SHOWING;
    }

    private static void playSequence(byte[] sequence) {
        int on = Math.max(180, 450 - 15 * length);
        int off = Math.max(80, 200 - 6 * length);
        for (int i = 0; i < length; i++) {
            drawPad(sequence[i], true);
            Delay.millis(on);
            drawPad(sequence[i], false);
            Delay.millis(off);
        }
    }

    /** Lets the player repeat the sequence; returns whether every pad matched in time. */
    private static boolean repeatSequence(byte[] sequence) {
        for (int i = 0; i < length; i++) {
            int pad = waitForPad();
            if (pad != sequence[i]) {
                if (pad >= 0) {
                    flashError(sequence[i]);
                }
                return false;
            }
        }
        return true;
    }

    private static void gameOver(String message) {
        int score = length - 1;
        if (length == MAX_STEPS) {
            score = length;
        }
        if (score > best) {
            best = score;
        }
        showStatus(message, TftTouchShield.RED);
        drawBest();
        drawScore(score);
        Delay.millis(1500);
        showStatus("Tap center", TftTouchShield.WHITE);
        drawHub("TAP");
        state = WAITING;
    }

    // Shows the pad that should have been pressed, blinking three times.
    private static void flashError(int pad) {
        for (int i = 0; i < 3; i++) {
            drawPad(pad, true);
            Delay.millis(150);
            drawPad(pad, false);
            Delay.millis(100);
        }
    }

    // ---- Input ----

    private static void waitForHubTap() {
        while (true) {
            if (TftTouchShield.readTouch()) {
                int dx = TftTouchShield.touchX() - CENTER_X;
                int dy = TftTouchShield.touchY() - CENTER_Y;
                waitForRelease();
                if (dx * dx + dy * dy <= HUB_RADIUS * HUB_RADIUS) {
                    return;
                }
            }
            Delay.millis(10);
        }
    }

    /**
     * Waits for a pad press, lighting the pad while it is held. Returns the pad, or -1 if nothing
     * was pressed within the timeout.
     */
    private static int waitForPad() {
        int started = Clock.millis();
        while (Clock.millis() - started < INPUT_TIMEOUT_MILLIS) {
            if (TftTouchShield.readTouch()) {
                int pad = padAt(TftTouchShield.touchX(), TftTouchShield.touchY());
                if (pad >= 0) {
                    drawPad(pad, true);
                    waitForRelease();
                    drawPad(pad, false);
                    return pad;
                }
            }
            Delay.millis(10);
        }
        return -1;
    }

    private static int padAt(int x, int y) {
        int dx = x - CENTER_X;
        int dy = y - CENTER_Y;
        int distance = dx * dx + dy * dy;
        if (distance <= HUB_RADIUS * HUB_RADIUS || distance > OUTER_RADIUS * OUTER_RADIUS) {
            return -1;
        }
        int pad = GREEN;
        if (dx >= 0) {
            pad = RED;
        }
        if (dy >= 0) {
            pad = pad + 2;
        }
        return pad;
    }

    private static void waitForRelease() {
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }

    // ---- Drawing ----

    /**
     * Fills one quarter of the ring (between the hub and the outer edge, leaving a gap along both
     * axes) row by row. Pads 0-3 are top-left, top-right, bottom-left, bottom-right.
     */
    private static void drawPad(int pad, boolean lit) {
        int color = padColor(pad, lit);
        int directionX = -1;
        if (pad == RED || pad == BLUE) {
            directionX = 1;
        }
        int directionY = -1;
        if (pad >= 2) {
            directionY = 1;
        }
        for (int dy = GAP; dy <= OUTER_RADIUS; dy++) {
            int outer = (int) Math.sqrt(OUTER_RADIUS * OUTER_RADIUS - dy * dy);
            int inner = GAP;
            if (dy < HUB_RADIUS + GAP) {
                inner = Math.max(GAP, (int) Math.sqrt((HUB_RADIUS + GAP) * (HUB_RADIUS + GAP) - dy * dy) + 1);
            }
            if (outer < inner) {
                continue;
            }
            int y = CENTER_Y + directionY * dy;
            if (directionX > 0) {
                TftTouchShield.drawHorizontalLine(CENTER_X + inner, y, outer - inner + 1, color);
            } else {
                TftTouchShield.drawHorizontalLine(CENTER_X - outer, y, outer - inner + 1, color);
            }
        }
    }

    private static int padColor(int pad, boolean lit) {
        if (pad == GREEN) {
            if (lit) {
                return GREEN_LIT;
            }
            return GREEN_DIM;
        }
        if (pad == RED) {
            if (lit) {
                return RED_LIT;
            }
            return RED_DIM;
        }
        if (pad == BLUE) {
            if (lit) {
                return BLUE_LIT;
            }
            return BLUE_DIM;
        }
        if (lit) {
            return YELLOW_LIT;
        }
        return YELLOW_DIM;
    }

    private static void drawHub(String text) {
        TftTouchShield.fillCircle(CENTER_X, CENTER_Y, HUB_RADIUS, TftTouchShield.BLACK);
        TftTouchShield.drawCircle(CENTER_X, CENTER_Y, HUB_RADIUS, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(CENTER_X - text.length() * 9, CENTER_Y - 12);
        TftTouchShield.print(text);
    }

    private static void drawScore(int score) {
        TftTouchShield.fillCircle(CENTER_X, CENTER_Y, HUB_RADIUS - 2, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(CENTER_X - 15, CENTER_Y - 24);
        TftTouchShield.print("SCORE");
        int digits = 1;
        if (score >= 10) {
            digits = 2;
        }
        if (score >= 100) {
            digits = 3;
        }
        TftTouchShield.setTextSize(4);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(CENTER_X - digits * 12, CENTER_Y - 10);
        TftTouchShield.print(score);
    }

    private static void drawBest() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(TftTouchShield.width() - 84, 12);
        TftTouchShield.print("Best ");
        TftTouchShield.print(best);
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 12);
        TftTouchShield.print(text);
        drawBest();
    }
}
