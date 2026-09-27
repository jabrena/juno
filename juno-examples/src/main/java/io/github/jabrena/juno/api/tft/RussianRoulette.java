package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * A cartoon game of Russian roulette against the computer on the ELEGOO 2.8" TFT touch screen
 * shield: a six-chamber cylinder, a few blanks' worth of nerve, and no gore — just a "click" or a
 * "BANG!".
 *
 * <p>Before a round, tap {@code LOAD} to load 1, 2 or 3 of the six chambers, then {@code START};
 * the cylinder spins and the players take turns, you first in odd rounds. On your turn tap
 * {@code PULL} to fire the chamber under the hammer, or use your one {@code SPIN} of the round to
 * re-spin the cylinder first. Chambers already fired since the last spin are known to be empty and
 * are drawn crossed out; each pull turns the cylinder one chamber. Whoever hears the bang loses
 * the round; the header keeps the score.
 *
 * <p>The computer knows the odds: it re-spins when the chance of a bullet under the hammer is
 * worse than a fresh spin would give, which is the right play; with only one spin each, the
 * interesting part is when to spend it.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class RussianRoulette {
    private static final int CHAMBERS = 6;

    // Chamber marks.
    private static final int UNKNOWN = 0;
    private static final int EMPTY = 1;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEADER_HEIGHT = 24;
    private static final int CENTER_X = 120;
    private static final int CENTER_Y = 136;
    private static final int CYLINDER_RADIUS = 72;
    private static final int CHAMBER_ORBIT = 44;
    private static final int CHAMBER_RADIUS = 16;
    private static final int STATUS_Y = 224;
    private static final int INFO_Y = 250;
    private static final int BUTTON_Y = 272;
    private static final int BUTTON_HEIGHT = 40;

    private static final int BACKGROUND = 0x18C3;
    private static final int STEEL = 0x8C71;
    private static final int STEEL_DARK = 0x4A49;
    private static final int HOLE = 0x10A2;
    private static final int BRASS = 0xE5A0;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON = 0xCD05;
    private static final int BUTTON_DISABLED = 0x39E7;

    private static int bullets = 1;
    private static int hammer;
    private static int round;
    private static int humanWins;
    private static int computerWins;
    private static boolean humanSpin;
    private static boolean computerSpin;
    private static boolean seeded;
    private static float angle;

    private RussianRoulette() {
    }

    public static void main(String[] args) {
        // loaded[i]: chamber i holds a bullet; seen[i]: fired empty since the last spin.
        boolean[] loaded = new boolean[CHAMBERS];
        byte[] seen = new byte[CHAMBERS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(BACKGROUND);
        drawHeader();
        drawCylinder(seen, loaded, false);
        while (true) {
            chooseBullets(seen, loaded);
            playRound(loaded, seen);
        }
    }

    // ---- Game flow ----

    /** The pre-round screen: cycle the bullet count, then START. */
    private static void chooseBullets(byte[] seen, boolean[] loaded) {
        showStatus("Load the cylinder", TftTouchShield.WHITE);
        showInfo();
        while (true) {
            drawButton(0, "LOAD", true);
            TftTouchShield.setTextColor(TftTouchShield.BLACK, BUTTON);
            TftTouchShield.print(" ");
            TftTouchShield.print(bullets);
            drawButton(1, "START", true);
            int button = waitForButton();
            if (button == 0) {
                bullets = bullets % 3 + 1;
                showInfo();
            } else {
                return;
            }
        }
    }

    private static void playRound(boolean[] loaded, byte[] seen) {
        if (!seeded) {
            Random.seed(Clock.micros());
            seeded = true;
        }
        round = round + 1;
        drawHeader();
        for (int i = 0; i < CHAMBERS; i++) {
            loaded[i] = false;
        }
        int placed = 0;
        while (placed < bullets) {
            int chamber = Random.nextInt(CHAMBERS);
            if (!loaded[chamber]) {
                loaded[chamber] = true;
                placed = placed + 1;
            }
        }
        humanSpin = true;
        computerSpin = true;
        spin(seen, loaded);
        boolean humanTurn = round % 2 == 1;
        while (true) {
            showInfo();
            boolean bang;
            if (humanTurn) {
                bang = humanMove(loaded, seen);
            } else {
                bang = computerMove(loaded, seen);
            }
            if (bang) {
                if (humanTurn) {
                    computerWins = computerWins + 1;
                } else {
                    humanWins = humanWins + 1;
                }
                drawHeader();
                if (humanTurn) {
                    showStatus("BANG! You lose", TftTouchShield.RED);
                } else {
                    showStatus("BANG! CPU loses", TftTouchShield.GREEN);
                }
                drawButton(0, "", false);
                drawButton(1, "NEXT", true);
                while (waitForButton() != 1) {
                    Delay.millis(10);
                }
                drawCylinder(seen, loaded, false);
                return;
            }
            humanTurn = !humanTurn;
        }
    }

    private static boolean humanMove(boolean[] loaded, byte[] seen) {
        showStatus("Your turn", TftTouchShield.YELLOW);
        while (true) {
            drawButton(0, "SPIN", humanSpin);
            drawButton(1, "PULL", true);
            int button = waitForButton();
            if (button == 0 && humanSpin) {
                humanSpin = false;
                spin(seen, loaded);
                showInfo();
                showStatus("Your turn: PULL", TftTouchShield.YELLOW);
            } else if (button == 1) {
                drawButton(0, "SPIN", false);
                drawButton(1, "PULL", false);
                return pull(loaded, seen);
            }
        }
    }

    private static boolean computerMove(boolean[] loaded, byte[] seen) {
        drawButton(0, "SPIN", false);
        drawButton(1, "PULL", false);
        showStatus("CPU's turn...", TftTouchShield.ORANGE);
        Delay.millis(900);
        // Chance of a bullet under the hammer, given the chambers known to be empty.
        int unknown = 0;
        for (int i = 0; i < CHAMBERS; i++) {
            if (seen[i] == UNKNOWN) {
                unknown = unknown + 1;
            }
        }
        // bullets / unknown > bullets / 6 exactly when some chambers are already known empty.
        if (computerSpin && unknown < CHAMBERS) {
            computerSpin = false;
            showStatus("CPU spins", TftTouchShield.ORANGE);
            spin(seen, loaded);
            showInfo();
            Delay.millis(500);
        }
        showStatus("CPU pulls...", TftTouchShield.ORANGE);
        Delay.millis(900);
        return pull(loaded, seen);
    }

    /** Fires the chamber under the hammer; returns true on a bang, else turns to the next one. */
    private static boolean pull(boolean[] loaded, byte[] seen) {
        if (loaded[hammer]) {
            for (int flash = 0; flash < 3; flash++) {
                TftTouchShield.fillCircle(CENTER_X, CENTER_Y, CYLINDER_RADIUS + 8, TftTouchShield.WHITE);
                Delay.millis(40);
                TftTouchShield.fillCircle(CENTER_X, CENTER_Y, CYLINDER_RADIUS + 8, TftTouchShield.RED);
                Delay.millis(60);
            }
            TftTouchShield.fillCircle(CENTER_X, CENTER_Y, CYLINDER_RADIUS + 8, BACKGROUND);
            TftTouchShield.setTextSize(5);
            TftTouchShield.setTextColor(TftTouchShield.RED, BACKGROUND);
            TftTouchShield.setCursor(CENTER_X - 75, CENTER_Y - 18);
            TftTouchShield.print("BANG!");
            Delay.millis(900);
            drawCylinder(seen, loaded, true);
            return true;
        }
        showStatus("*click*", TftTouchShield.WHITE);
        seen[hammer] = EMPTY;
        drawCylinder(seen, loaded, false);
        Delay.millis(500);
        // Turn the cylinder one chamber, animated.
        for (int step = 1; step <= 6; step++) {
            angle = angle - (float) (Math.PI / 3) / 6;
            drawCylinder(seen, loaded, false);
            Delay.millis(30);
        }
        hammer = (hammer + 1) % CHAMBERS;
        angle = 0;
        drawCylinder(seen, loaded, false);
        return false;
    }

    /** Spins the cylinder to a random chamber; everything fired so far becomes unknown again. */
    private static void spin(byte[] seen, boolean[] loaded) {
        for (int i = 0; i < CHAMBERS; i++) {
            seen[i] = UNKNOWN;
        }
        int target = Random.nextInt(CHAMBERS);
        // A few fast turns slowing down, ending with `target` under the hammer.
        int steps = 18 + (target - hammer + CHAMBERS) % CHAMBERS;
        showStatus("Spinning...", TftTouchShield.WHITE);
        for (int step = 0; step < steps; step++) {
            for (int frame = 1; frame <= 3; frame++) {
                angle = -(float) (Math.PI / 3) * frame / 3;
                drawCylinder(seen, loaded, false);
                Delay.millis(4 + step * step / 8);
            }
            hammer = (hammer + 1) % CHAMBERS;
            angle = 0;
        }
        drawCylinder(seen, loaded, false);
    }

    // ---- Input ----

    /** Waits for a tap on the left (0) or right (1) button. */
    private static int waitForButton() {
        while (true) {
            if (TftTouchShield.readTouch()) {
                int x = TftTouchShield.touchX();
                int y = TftTouchShield.touchY();
                waitForRelease();
                if (y >= BUTTON_Y && y < BUTTON_Y + BUTTON_HEIGHT) {
                    if (x < WIDTH / 2) {
                        return 0;
                    }
                    return 1;
                }
            }
            Delay.millis(10);
        }
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
     * Draws the cylinder from the front, with the chamber under the hammer at the top; {@code angle}
     * turns it part of the way to the next chamber. {@code reveal} shows where the bullets are.
     */
    private static void drawCylinder(byte[] seen, boolean[] loaded, boolean reveal) {
        TftTouchShield.fillCircle(CENTER_X, CENTER_Y, CYLINDER_RADIUS + 8, BACKGROUND);
        // The barrel and hammer mark above the top chamber.
        TftTouchShield.fillRect(CENTER_X - 10, CENTER_Y - CYLINDER_RADIUS - 8, 20, 10, STEEL_DARK);
        TftTouchShield.fillRect(CENTER_X - 3, CENTER_Y - CYLINDER_RADIUS - 8, 6, 5, TftTouchShield.RED);
        TftTouchShield.fillCircle(CENTER_X, CENTER_Y, CYLINDER_RADIUS, STEEL);
        TftTouchShield.drawCircle(CENTER_X, CENTER_Y, CYLINDER_RADIUS, STEEL_DARK);
        TftTouchShield.fillCircle(CENTER_X, CENTER_Y, 10, STEEL_DARK);
        for (int i = 0; i < CHAMBERS; i++) {
            int chamber = (hammer + i) % CHAMBERS;
            float a = (float) (Math.PI / 3) * i + angle - (float) (Math.PI / 2);
            int cx = CENTER_X + Math.round((float) Math.cos(a) * CHAMBER_ORBIT);
            int cy = CENTER_Y + Math.round((float) Math.sin(a) * CHAMBER_ORBIT);
            TftTouchShield.fillCircle(cx, cy, CHAMBER_RADIUS, HOLE);
            if (reveal && loaded[chamber]) {
                TftTouchShield.fillCircle(cx, cy, CHAMBER_RADIUS - 4, BRASS);
                TftTouchShield.fillCircle(cx, cy, 4, 0xB400);
            } else if (seen[chamber] == EMPTY) {
                // Known empty: crossed out.
                for (int d = -8; d <= 8; d++) {
                    TftTouchShield.fillRect(cx + d - 1, cy + d - 1, 3, 3, STEEL_DARK);
                    TftTouchShield.fillRect(cx + d - 1, cy - d - 1, 3, 3, STEEL_DARK);
                }
            }
        }
    }

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(6, 8);
        TftTouchShield.print("ROUND ");
        TftTouchShield.print(round);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, HEADER_BACKGROUND);
        TftTouchShield.setCursor(84, 4);
        TftTouchShield.print("You ");
        TftTouchShield.print(humanWins);
        TftTouchShield.setTextColor(TftTouchShield.ORANGE, HEADER_BACKGROUND);
        TftTouchShield.print(" CPU ");
        TftTouchShield.print(computerWins);
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, STATUS_Y, WIDTH, 20, BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, BACKGROUND);
        TftTouchShield.setCursor((WIDTH - text.length() * 12) / 2, STATUS_Y + 2);
        TftTouchShield.print(text);
    }

    /** Bullets loaded and the spins still available. */
    private static void showInfo() {
        TftTouchShield.fillRect(0, INFO_Y, WIDTH, 12, BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, BACKGROUND);
        TftTouchShield.setCursor(8, INFO_Y + 2);
        TftTouchShield.print("Bullets ");
        TftTouchShield.print(bullets);
        TftTouchShield.print("   Spins: you ");
        if (humanSpin) {
            TftTouchShield.print("1");
        } else {
            TftTouchShield.print("0");
        }
        TftTouchShield.print(", CPU ");
        if (computerSpin) {
            TftTouchShield.print("1");
        } else {
            TftTouchShield.print("0");
        }
    }

    private static void drawButton(int index, String label, boolean enabled) {
        int x = 8 + index * 116;
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        TftTouchShield.fillRect(x, BUTTON_Y, 108, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(x, BUTTON_Y, 108, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(x + (108 - label.length() * 12) / 2, BUTTON_Y + 13);
        TftTouchShield.print(label);
    }
}
