package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Solitaire Yahtzee on the ELEGOO 2.8" TFT touch screen shield: thirteen rounds, one scorecard.
 *
 * <p>Each round tap {@code ROLL} up to three times; between rolls, tap a die to hold it (held dice
 * turn yellow) or release it. After the first roll the open boxes of the scorecard preview what the
 * dice would score, and tapping one scores it and ends the round. The upper section earns a 35-point
 * bonus at 63 or more. Extra Yahtzees follow the official joker rules: each is worth 100 bonus
 * points once the Yahtzee box holds 50, it must go in the matching upper box while that box is open,
 * and otherwise it scores full house and both straights at full value. The header keeps the best
 * score since power-up.
 *
 * <p>Categories are numbered 0-12: 0-5 are the upper boxes (aces to sixes), then three of a kind,
 * four of a kind, full house, small straight, large straight, Yahtzee and chance. An open box holds
 * -1.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Yahtzee {
    private static final int DICE = 5;
    private static final int CATEGORIES = 13;
    private static final int ROUNDS = 13;
    private static final int OPEN = -1;

    private static final int THREE_KIND = 6;
    private static final int FOUR_KIND = 7;
    private static final int FULL_HOUSE = 8;
    private static final int SMALL_STRAIGHT = 9;
    private static final int LARGE_STRAIGHT = 10;
    private static final int YAHTZEE = 11;
    private static final int CHANCE = 12;

    private static final int UPPER_BONUS_AT = 63;
    private static final int UPPER_BONUS = 35;
    private static final int YAHTZEE_BONUS = 100;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEADER_HEIGHT = 24;
    private static final int CARD_TOP = 26;
    private static final int ROW_HEIGHT = 22;
    private static final int ROWS = 7;
    private static final int COLUMN_WIDTH = 120;
    private static final int DICE_Y = 188;
    private static final int DIE = 40;
    private static final int DICE_X = 8;
    private static final int DICE_SPACING = 46;
    private static final int MESSAGE_Y = 234;
    private static final int BUTTON_Y = 258;
    private static final int BUTTON_HEIGHT = 54;

    private static final int TABLE = 0x0266;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int ROW_EVEN = 0x18E3;
    private static final int ROW_ODD = 0x2124;
    private static final int PREVIEW = 0x7BEF;
    private static final int HELD = TftTouchShield.YELLOW;
    private static final int BUTTON = 0xCD05;
    private static final int BUTTON_DISABLED = 0x39E7;

    private static int round;
    private static int rollsLeft;
    private static int yahtzeeBonus;
    private static int best;
    private static boolean gameOver;
    private static boolean seeded;
    private static int tapX;
    private static int tapY;

    private Yahtzee() {
    }

    public static void main(String[] args) {
        int[] dice = new int[DICE];
        boolean[] held = new boolean[DICE];
        int[] scores = new int[CATEGORIES];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        newGame(dice, held, scores);

        while (true) {
            waitForTap();
            if (tapY >= BUTTON_Y) {
                if (gameOver) {
                    newGame(dice, held, scores);
                } else if (rollsLeft > 0) {
                    roll(dice, held, scores);
                }
            } else if (tapY >= DICE_Y && tapY < DICE_Y + DIE + 4) {
                int die = (tapX - DICE_X + 3) / DICE_SPACING;
                if (!gameOver && rollsLeft > 0 && rollsLeft < 3 && die >= 0 && die < DICE) {
                    held[die] = !held[die];
                    drawDie(die, dice[die], held[die]);
                }
            } else if (tapY >= CARD_TOP && tapY < CARD_TOP + ROWS * ROW_HEIGHT) {
                int category = categoryAt(tapX, tapY);
                if (!gameOver && rollsLeft < 3 && category >= 0 && allowed(category, dice, scores)) {
                    scoreBox(category, dice, held, scores);
                }
            }
        }
    }

    // ---- Game flow ----

    private static void newGame(int[] dice, boolean[] held, int[] scores) {
        for (int i = 0; i < CATEGORIES; i++) {
            scores[i] = OPEN;
        }
        round = 1;
        yahtzeeBonus = 0;
        gameOver = false;
        TftTouchShield.fillScreen(TABLE);
        startRound(dice, held, scores);
    }

    private static void startRound(int[] dice, boolean[] held, int[] scores) {
        rollsLeft = 3;
        for (int i = 0; i < DICE; i++) {
            held[i] = false;
            dice[i] = 0;
        }
        drawHeader(scores);
        drawCard(dice, scores);
        drawDice(dice, held);
        showMessage("Roll the dice", TftTouchShield.WHITE);
        drawButton();
    }

    private static void roll(int[] dice, boolean[] held, int[] scores) {
        if (!seeded) {
            Random.seed(Clock.micros());
            seeded = true;
        }
        // A short tumble: the free dice flicker through random faces before settling.
        for (int frame = 0; frame < 6; frame++) {
            for (int i = 0; i < DICE; i++) {
                if (!held[i]) {
                    dice[i] = Random.nextInt(1, 7);
                    drawDie(i, dice[i], false);
                }
            }
            Delay.millis(60);
        }
        rollsLeft = rollsLeft - 1;
        drawCard(dice, scores);
        drawButton();
        if (isYahtzee(dice)) {
            showMessage("YAHTZEE!", TftTouchShield.YELLOW);
        } else if (rollsLeft == 0) {
            showMessage("Pick a box", TftTouchShield.WHITE);
        } else {
            showMessage("Hold dice or roll", TftTouchShield.WHITE);
        }
    }

    private static void scoreBox(int category, int[] dice, boolean[] held, int[] scores) {
        if (isYahtzee(dice) && scores[YAHTZEE] == 50) {
            yahtzeeBonus = yahtzeeBonus + YAHTZEE_BONUS;
        }
        scores[category] = score(category, dice, scores);
        if (round == ROUNDS) {
            gameOver = true;
            int total = total(scores);
            best = Math.max(best, total);
            drawHeader(scores);
            drawCard(dice, scores);
            showMessage("Game over", TftTouchShield.YELLOW);
            drawButton();
            return;
        }
        round = round + 1;
        startRound(dice, held, scores);
    }

    // ---- Scoring ----

    /** The points {@code dice} would score in {@code category}, applying the joker rule. */
    private static int score(int category, int[] dice, int[] scores) {
        int sum = 0;
        for (int i = 0; i < DICE; i++) {
            sum = sum + dice[i];
        }
        boolean joker = isJoker(dice, scores);
        if (category < THREE_KIND) {
            return count(dice, category + 1) * (category + 1);
        }
        if (category == THREE_KIND) {
            if (maxOfAKind(dice) >= 3) {
                return sum;
            }
            return 0;
        }
        if (category == FOUR_KIND) {
            if (maxOfAKind(dice) >= 4) {
                return sum;
            }
            return 0;
        }
        if (category == FULL_HOUSE) {
            if (joker || isFullHouse(dice)) {
                return 25;
            }
            return 0;
        }
        if (category == SMALL_STRAIGHT) {
            if (joker || run(dice) >= 4) {
                return 30;
            }
            return 0;
        }
        if (category == LARGE_STRAIGHT) {
            if (joker || run(dice) == 5) {
                return 40;
            }
            return 0;
        }
        if (category == YAHTZEE) {
            if (isYahtzee(dice)) {
                return 50;
            }
            return 0;
        }
        return sum;
    }

    /**
     * Whether {@code category} may be scored now: any open box, except that a joker Yahtzee must use
     * its upper box while that is open, and then a lower box while any is open.
     */
    private static boolean allowed(int category, int[] dice, int[] scores) {
        if (scores[category] != OPEN) {
            return false;
        }
        if (!isJoker(dice, scores)) {
            return true;
        }
        int upper = dice[0] - 1;
        if (scores[upper] == OPEN) {
            return category == upper;
        }
        for (int i = THREE_KIND; i < CATEGORIES; i++) {
            if (scores[i] == OPEN) {
                return category >= THREE_KIND;
            }
        }
        return true;
    }

    private static boolean isJoker(int[] dice, int[] scores) {
        return isYahtzee(dice) && scores[YAHTZEE] != OPEN;
    }

    private static boolean isYahtzee(int[] dice) {
        return dice[0] != 0 && maxOfAKind(dice) == 5;
    }

    private static boolean isFullHouse(int[] dice) {
        boolean three = false;
        boolean two = false;
        for (int face = 1; face <= 6; face++) {
            int n = count(dice, face);
            if (n == 3) {
                three = true;
            } else if (n == 2) {
                two = true;
            }
        }
        return three && two;
    }

    private static int maxOfAKind(int[] dice) {
        int most = 0;
        for (int face = 1; face <= 6; face++) {
            most = Math.max(most, count(dice, face));
        }
        return most;
    }

    /** Length of the longest run of consecutive faces. */
    private static int run(int[] dice) {
        int longest = 0;
        int current = 0;
        for (int face = 1; face <= 6; face++) {
            if (count(dice, face) > 0) {
                current = current + 1;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private static int count(int[] dice, int face) {
        int n = 0;
        for (int i = 0; i < DICE; i++) {
            if (dice[i] == face) {
                n = n + 1;
            }
        }
        return n;
    }

    private static int upperSum(int[] scores) {
        int sum = 0;
        for (int i = 0; i < THREE_KIND; i++) {
            if (scores[i] != OPEN) {
                sum = sum + scores[i];
            }
        }
        return sum;
    }

    private static int total(int[] scores) {
        int sum = yahtzeeBonus;
        for (int i = 0; i < CATEGORIES; i++) {
            if (scores[i] != OPEN) {
                sum = sum + scores[i];
            }
        }
        if (upperSum(scores) >= UPPER_BONUS_AT) {
            sum = sum + UPPER_BONUS;
        }
        return sum;
    }

    // ---- Input ----

    /** Returns the category under a scorecard tap, or -1 for the upper bonus row. */
    private static int categoryAt(int x, int y) {
        int row = (y - CARD_TOP) / ROW_HEIGHT;
        if (x < COLUMN_WIDTH) {
            if (row == ROWS - 1) {
                return -1;
            }
            return row;
        }
        return THREE_KIND + row;
    }

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        tapX = TftTouchShield.touchX();
        tapY = TftTouchShield.touchY();
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

    private static void drawHeader(int[] scores) {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 4);
        if (gameOver) {
            TftTouchShield.print("Best ");
            TftTouchShield.print(best);
        } else {
            TftTouchShield.print("Rnd ");
            TftTouchShield.print(round);
            TftTouchShield.print("/");
            TftTouchShield.print(ROUNDS);
        }
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(124, 4);
        TftTouchShield.print("Score ");
        TftTouchShield.print(total(scores));
    }

    private static void drawCard(int[] dice, int[] scores) {
        for (int category = 0; category < CATEGORIES; category++) {
            drawBox(category, dice, scores);
        }
        // Upper bonus row: progress towards 63, then the 35 points once earned.
        int y = CARD_TOP + (ROWS - 1) * ROW_HEIGHT;
        drawRowBackground(0, y, ROWS - 1);
        drawLabel(0, y, "Bonus", ROWS - 1);
        int upper = upperSum(scores);
        int background = rowColor(ROWS - 1);
        if (upper >= UPPER_BONUS_AT) {
            drawValue(0, y, UPPER_BONUS, TftTouchShield.WHITE, background);
        } else {
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(PREVIEW, background);
            int digits = digitCount(upper);
            TftTouchShield.setCursor(COLUMN_WIDTH - 6 - (digits + 3) * 6, y + 7);
            TftTouchShield.print(upper);
            TftTouchShield.print("/63");
        }
    }

    private static void drawBox(int category, int[] dice, int[] scores) {
        int x = 0;
        int row = category;
        if (category >= THREE_KIND) {
            x = COLUMN_WIDTH;
            row = category - THREE_KIND;
        }
        int y = CARD_TOP + row * ROW_HEIGHT;
        drawRowBackground(x, y, row);
        drawLabel(x, y, label(category), row);
        int background = rowColor(row);
        if (scores[category] != OPEN) {
            drawValue(x, y, scores[category], TftTouchShield.WHITE, background);
        } else if (dice[0] != 0 && !gameOver && allowed(category, dice, scores)) {
            drawValue(x, y, score(category, dice, scores), PREVIEW, background);
        }
    }

    private static void drawRowBackground(int x, int y, int row) {
        TftTouchShield.fillRect(x, y, COLUMN_WIDTH, ROW_HEIGHT, rowColor(row));
        TftTouchShield.drawVerticalLine(COLUMN_WIDTH - 1, y, ROW_HEIGHT, TABLE);
    }

    private static int rowColor(int row) {
        if (row % 2 == 0) {
            return ROW_EVEN;
        }
        return ROW_ODD;
    }

    private static void drawLabel(int x, int y, String text, int row) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, rowColor(row));
        TftTouchShield.setCursor(x + 4, y + 7);
        TftTouchShield.print(text);
    }

    private static void drawValue(int x, int y, int value, int color, int background) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, background);
        TftTouchShield.setCursor(x + COLUMN_WIDTH - 6 - digitCount(value) * 12, y + 4);
        TftTouchShield.print(value);
    }

    private static int digitCount(int value) {
        if (value >= 100) {
            return 3;
        }
        if (value >= 10) {
            return 2;
        }
        return 1;
    }

    private static String label(int category) {
        if (category == 0) {
            return "Aces";
        }
        if (category == 1) {
            return "Twos";
        }
        if (category == 2) {
            return "Threes";
        }
        if (category == 3) {
            return "Fours";
        }
        if (category == 4) {
            return "Fives";
        }
        if (category == 5) {
            return "Sixes";
        }
        if (category == THREE_KIND) {
            return "3 of a kind";
        }
        if (category == FOUR_KIND) {
            return "4 of a kind";
        }
        if (category == FULL_HOUSE) {
            return "Full house";
        }
        if (category == SMALL_STRAIGHT) {
            return "Sm straight";
        }
        if (category == LARGE_STRAIGHT) {
            return "Lg straight";
        }
        if (category == YAHTZEE) {
            return "YAHTZEE";
        }
        return "Chance";
    }

    private static void drawDice(int[] dice, boolean[] held) {
        for (int i = 0; i < DICE; i++) {
            drawDie(i, dice[i], held[i]);
        }
    }

    /** Draws one die; face 0 is a blank die before the first roll of a round. */
    private static void drawDie(int index, int face, boolean isHeld) {
        int x = DICE_X + index * DICE_SPACING;
        int color = TftTouchShield.WHITE;
        if (face == 0) {
            color = TftTouchShield.GRAY;
        } else if (isHeld) {
            color = HELD;
        }
        TftTouchShield.fillRect(x, DICE_Y, DIE, DIE, color);
        TftTouchShield.drawRect(x, DICE_Y, DIE, DIE, TftTouchShield.BLACK);
        int low = 10;
        int middle = 20;
        int high = 30;
        if (face % 2 == 1) {
            pip(x + middle, DICE_Y + middle);
        }
        if (face >= 2) {
            pip(x + low, DICE_Y + low);
            pip(x + high, DICE_Y + high);
        }
        if (face >= 4) {
            pip(x + high, DICE_Y + low);
            pip(x + low, DICE_Y + high);
        }
        if (face == 6) {
            pip(x + low, DICE_Y + middle);
            pip(x + high, DICE_Y + middle);
        }
    }

    private static void pip(int x, int y) {
        TftTouchShield.fillCircle(x, y, 4, TftTouchShield.BLACK);
    }

    private static void showMessage(String text, int color) {
        TftTouchShield.fillRect(0, MESSAGE_Y, WIDTH, 20, TABLE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, TABLE);
        TftTouchShield.setCursor((WIDTH - text.length() * 12) / 2, MESSAGE_Y + 2);
        TftTouchShield.print(text);
    }

    private static void drawButton() {
        int color = BUTTON;
        if (!gameOver && rollsLeft == 0) {
            color = BUTTON_DISABLED;
        }
        TftTouchShield.fillRect(8, BUTTON_Y, WIDTH - 16, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(8, BUTTON_Y, WIDTH - 16, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        if (gameOver) {
            TftTouchShield.setCursor((WIDTH - 8 * 18) / 2, BUTTON_Y + 16);
            TftTouchShield.print("NEW GAME");
        } else {
            TftTouchShield.setCursor((WIDTH - 8 * 18) / 2, BUTTON_Y + 16);
            TftTouchShield.print("ROLL (");
            TftTouchShield.print(rollsLeft);
            TftTouchShield.print(")");
        }
    }
}
