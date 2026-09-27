package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * A classic three-reel slot machine on the ELEGOO 2.8" TFT touch screen shield, played for
 * credits only. Tap {@code BET} to stake 1, 2 or 3 credits and {@code SPIN} to pull; the reels stop
 * one after another from the left and the center row is the pay line.
 *
 * <p>Pays, per credit bet: three 7s 200, three BARs 50, three bells 20, three plums 14, three
 * lemons 6 and three cherries 10; two cherries from the left pay 6 and a single cherry on the
 * first reel pays 1. You start with {@value #START_CREDITS} credits and get a fresh stack when you
 * run out. Like a real machine it keeps a little for the house: every reel has 20 stops, with
 * fewer of the valuable symbols, which makes the long-run payback 89.6%.
 *
 * <p>The symbols are drawn from filled rectangles, circles and ellipses (made of horizontal lines),
 * and each reel's strip is a string of symbol digits.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class SlotMachine {
    private static final int START_CREDITS = 100;
    private static final int MAX_BET = 3;
    private static final int STOPS = 20;
    private static final int REELS = 3;

    // Symbols.
    private static final int CHERRY = 0;
    private static final int LEMON = 1;
    private static final int PLUM = 2;
    private static final int BELL = 3;
    private static final int BAR = 4;
    private static final int SEVEN = 5;

    // The reel strips: 4 cherries, 7 lemons, 4 plums, 2 bells, 2 BARs and one 7 each.
    private static final String REEL_1 = "01213014120315241021";
    private static final String REEL_2 = "10213102410513212014";
    private static final String REEL_3 = "12031412015132041201";

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEADER_HEIGHT = 22;
    private static final int TABLE_Y = 26;
    private static final int REEL_Y = 76;
    private static final int REEL_X = 20;
    private static final int REEL_SPACING = 70;
    private static final int CELL_WIDTH = 60;
    private static final int CELL_HEIGHT = 50;
    private static final int MESSAGE_Y = 232;
    private static final int BUTTON_Y = 262;
    private static final int BUTTON_HEIGHT = 50;

    private static final int CABINET = 0x2003;
    private static final int TRIM = 0xFEA0;
    private static final int PAPER = 0xFFBD;
    private static final int PAYLINE = TftTouchShield.RED;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON = 0xCD05;
    private static final int BUTTON_DISABLED = 0x39E7;

    private static int credits;
    private static int bet;
    private static int lastWin;
    private static boolean seeded;

    private SlotMachine() {
    }

    public static void main(String[] args) {
        // positions[r]: the strip index shown on the pay line of reel r.
        int[] positions = new int[REELS];
        int[] ticks = new int[REELS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        credits = START_CREDITS;
        bet = 1;
        TftTouchShield.fillScreen(CABINET);
        drawHeader();
        drawPayTable();
        drawFrame();
        for (int reel = 0; reel < REELS; reel++) {
            positions[reel] = reel * 7;
            drawReel(reel, positions[reel]);
        }
        showMessage("Tap SPIN to play", TftTouchShield.WHITE);
        drawButtons(true);

        while (true) {
            int button = waitForButton();
            if (button == 0) {
                bet = bet % MAX_BET + 1;
                drawHeader();
                drawButtons(true);
            } else {
                if (credits == 0) {
                    credits = START_CREDITS;
                    lastWin = 0;
                    drawHeader();
                }
                spin(positions, ticks);
            }
        }
    }

    // ---- Game flow ----

    private static void spin(int[] positions, int[] ticks) {
        if (!seeded) {
            Random.seed(Clock.micros());
            seeded = true;
        }
        int stake = Math.min(bet, credits);
        credits = credits - stake;
        lastWin = 0;
        drawHeader();
        drawButtons(false);
        showMessage("", TftTouchShield.WHITE);
        drawPayLine(false);
        // Pick the stops first, then spin each reel for enough ticks to land on its stop.
        for (int reel = 0; reel < REELS; reel++) {
            int stop = Random.nextInt(STOPS);
            int base = 14 + reel * 8;
            ticks[reel] = base + ((positions[reel] - stop - base) % STOPS + STOPS) % STOPS;
        }
        boolean moving = true;
        while (moving) {
            moving = false;
            for (int reel = 0; reel < REELS; reel++) {
                if (ticks[reel] > 0) {
                    // The strip rolls downwards: the symbol above moves onto the pay line.
                    positions[reel] = (positions[reel] + STOPS - 1) % STOPS;
                    ticks[reel] = ticks[reel] - 1;
                    drawReel(reel, positions[reel]);
                    moving = true;
                }
            }
            Delay.millis(25);
        }
        int a = symbol(0, positions[0]);
        int b = symbol(1, positions[1]);
        int c = symbol(2, positions[2]);
        int pay = payout(a, b, c);
        if (pay > 0) {
            lastWin = pay * stake;
            credits = credits + lastWin;
            for (int flash = 0; flash < 4; flash++) {
                drawPayLine(true);
                Delay.millis(150);
                drawPayLine(false);
                Delay.millis(100);
            }
            drawPayLine(true);
            if (pay >= 50) {
                showMessage("JACKPOT!", TftTouchShield.YELLOW);
            } else {
                showMessage("WINNER!", TftTouchShield.GREEN);
            }
        } else if (credits == 0) {
            showMessage("Out of credits: SPIN", TftTouchShield.RED);
        } else {
            showMessage("Try again", TftTouchShield.GRAY);
        }
        drawHeader();
        drawButtons(true);
    }

    /** Credits paid per credit bet for the pay-line symbols {@code a}, {@code b}, {@code c}. */
    private static int payout(int a, int b, int c) {
        if (a == b && b == c) {
            if (a == SEVEN) {
                return 200;
            }
            if (a == BAR) {
                return 50;
            }
            if (a == BELL) {
                return 20;
            }
            if (a == PLUM) {
                return 14;
            }
            if (a == CHERRY) {
                return 10;
            }
            return 6;
        }
        if (a == CHERRY && b == CHERRY) {
            return 6;
        }
        if (a == CHERRY) {
            return 1;
        }
        return 0;
    }

    private static int symbol(int reel, int position) {
        int index = (position % STOPS + STOPS) % STOPS;
        if (reel == 0) {
            return REEL_1.charAt(index) - '0';
        }
        if (reel == 1) {
            return REEL_2.charAt(index) - '0';
        }
        return REEL_3.charAt(index) - '0';
    }

    // ---- Input ----

    /** Waits for BET (0) or SPIN (1). */
    private static int waitForButton() {
        while (true) {
            if (TftTouchShield.readTouch()) {
                int x = TftTouchShield.touchX();
                int y = TftTouchShield.touchY();
                waitForRelease();
                if (y >= BUTTON_Y) {
                    if (x < 80) {
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

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(6, 7);
        TftTouchShield.print("CREDITS ");
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.print(credits);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(100, 7);
        TftTouchShield.print("BET ");
        TftTouchShield.print(bet);
        TftTouchShield.setCursor(160, 7);
        TftTouchShield.print("WIN ");
        TftTouchShield.setTextColor(TftTouchShield.GREEN, HEADER_BACKGROUND);
        TftTouchShield.print(lastWin);
    }

    private static void drawPayTable() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TRIM, CABINET);
        TftTouchShield.setCursor(8, TABLE_Y);
        TftTouchShield.print("7 7 7     200   BELL x3    20");
        TftTouchShield.setCursor(8, TABLE_Y + 10);
        TftTouchShield.print("BAR x3     50   PLUM x3    14");
        TftTouchShield.setCursor(8, TABLE_Y + 20);
        TftTouchShield.print("CHERRY x3  10   LEMON x3    6");
        TftTouchShield.setCursor(8, TABLE_Y + 30);
        TftTouchShield.print("CHERRY x2   6   CHERRY      1");
    }

    private static void drawFrame() {
        int left = REEL_X - 8;
        int width = REELS * REEL_SPACING - (REEL_SPACING - CELL_WIDTH) + 16;
        TftTouchShield.fillRect(left, REEL_Y - 8, width, 3 * CELL_HEIGHT + 16, TRIM);
        TftTouchShield.fillRect(left + 3, REEL_Y - 5, width - 6, 3 * CELL_HEIGHT + 10, TftTouchShield.BLACK);
        drawPayLine(false);
    }

    /** The pay-line markers either side of the middle row; lit when a spin wins. */
    private static void drawPayLine(boolean lit) {
        int color = PAYLINE;
        if (lit) {
            color = TftTouchShield.YELLOW;
        }
        int y = REEL_Y + CELL_HEIGHT + CELL_HEIGHT / 2;
        TftTouchShield.fillRect(0, y - 3, REEL_X - 9, 7, color);
        TftTouchShield.fillRect(REEL_X + REELS * REEL_SPACING - 1, y - 3, WIDTH - REEL_X - REELS * REEL_SPACING + 1, 7,
                color);
        for (int reel = 0; reel < REELS; reel++) {
            int x = REEL_X + reel * REEL_SPACING;
            TftTouchShield.drawRect(x - 1, REEL_Y + CELL_HEIGHT - 1, CELL_WIDTH + 2, CELL_HEIGHT + 2, color);
        }
    }

    /** The three visible symbols of a reel: above, on, and below the pay line. */
    private static void drawReel(int reel, int position) {
        int x = REEL_X + reel * REEL_SPACING;
        for (int row = 0; row < 3; row++) {
            int y = REEL_Y + row * CELL_HEIGHT;
            // Top and bottom rows are shaded a little, like a curved reel.
            int paper = PAPER;
            if (row != 1) {
                paper = 0xC618;
            }
            TftTouchShield.fillRect(x, y, CELL_WIDTH, CELL_HEIGHT, paper);
            drawSymbol(symbol(reel, position - 1 + row), x + CELL_WIDTH / 2, y + CELL_HEIGHT / 2, paper);
        }
    }

    private static void drawSymbol(int symbol, int cx, int cy, int paper) {
        if (symbol == SEVEN) {
            TftTouchShield.setTextSize(5);
            TftTouchShield.setTextColor(TftTouchShield.RED, paper);
            TftTouchShield.setCursor(cx - 14, cy - 18);
            TftTouchShield.print("7");
        } else if (symbol == BAR) {
            TftTouchShield.fillRect(cx - 25, cy - 11, 50, 22, TftTouchShield.BLACK);
            TftTouchShield.drawRect(cx - 23, cy - 9, 46, 18, TftTouchShield.WHITE);
            TftTouchShield.setTextSize(2);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
            TftTouchShield.setCursor(cx - 17, cy - 7);
            TftTouchShield.print("BAR");
        } else if (symbol == BELL) {
            int gold = 0xFE60;
            TftTouchShield.fillCircle(cx, cy - 6, 12, gold);
            fillEllipse(cx, cy + 8, 18, 6, gold);
            TftTouchShield.fillRect(cx - 12, cy - 6, 25, 14, gold);
            TftTouchShield.fillCircle(cx, cy + 14, 4, 0x8200);
            TftTouchShield.fillRect(cx - 2, cy - 21, 5, 5, 0x8200);
        } else if (symbol == PLUM) {
            fillEllipse(cx, cy + 3, 17, 14, 0x780F);
            fillEllipse(cx - 6, cy - 2, 5, 4, 0xB2D7);
            TftTouchShield.fillRect(cx - 1, cy - 17, 3, 7, 0x4200);
            fillEllipse(cx + 7, cy - 14, 7, 3, 0x2444);
        } else if (symbol == LEMON) {
            fillEllipse(cx, cy, 20, 13, 0xFFE0);
            fillEllipse(cx - 20, cy, 4, 3, 0xFFE0);
            fillEllipse(cx + 20, cy, 4, 3, 0xFFE0);
            fillEllipse(cx - 6, cy - 5, 6, 3, 0xFFF6);
        } else {
            // Two cherries hanging from a stem made of small steps.
            TftTouchShield.fillCircle(cx - 10, cy + 9, 9, 0xD000);
            TftTouchShield.fillCircle(cx + 10, cy + 11, 9, 0xD000);
            TftTouchShield.fillCircle(cx - 13, cy + 6, 3, 0xFB2C);
            TftTouchShield.fillCircle(cx + 7, cy + 8, 3, 0xFB2C);
            for (int i = 0; i < 14; i++) {
                TftTouchShield.fillRect(cx - 10 + i * 10 / 14, cy - i - 1, 2, 2, 0x2444);
                TftTouchShield.fillRect(cx + 10 - i * 10 / 14, cy + 1 - i, 2, 2, 0x2444);
            }
            fillEllipse(cx + 6, cy - 16, 7, 3, 0x2444);
        }
    }

    /** A filled ellipse from horizontal lines. */
    private static void fillEllipse(int cx, int cy, int rx, int ry, int color) {
        for (int dy = -ry; dy <= ry; dy++) {
            float t = 1.0f - (float) (dy * dy) / (ry * ry);
            int half = Math.round(rx * (float) Math.sqrt(t));
            TftTouchShield.drawHorizontalLine(cx - half, cy + dy, 2 * half + 1, color);
        }
    }

    private static void showMessage(String text, int color) {
        TftTouchShield.fillRect(0, MESSAGE_Y, WIDTH, 22, CABINET);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, CABINET);
        TftTouchShield.setCursor((WIDTH - text.length() * 12) / 2, MESSAGE_Y + 3);
        TftTouchShield.print(text);
    }

    private static void drawButtons(boolean enabled) {
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        TftTouchShield.fillRect(8, BUTTON_Y, 64, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(8, BUTTON_Y, 64, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(22, BUTTON_Y + 9);
        TftTouchShield.print("BET");
        TftTouchShield.setCursor(34, BUTTON_Y + 29);
        TftTouchShield.print(bet);
        int spin = TftTouchShield.RED;
        if (!enabled) {
            spin = BUTTON_DISABLED;
        }
        TftTouchShield.fillRect(80, BUTTON_Y, WIDTH - 88, BUTTON_HEIGHT, spin);
        TftTouchShield.drawRect(80, BUTTON_Y, WIDTH - 88, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, spin);
        TftTouchShield.setCursor(80 + (WIDTH - 88 - 4 * 18) / 2, BUTTON_Y + 14);
        TftTouchShield.print("SPIN");
    }
}
