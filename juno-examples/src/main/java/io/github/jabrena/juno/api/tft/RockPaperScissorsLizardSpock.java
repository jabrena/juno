package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Rock, Paper, Scissors, Lizard, Spock (Sam Kass and Karen Bryla's extension of the playground
 * game, made famous by <i>The Big Bang Theory</i>) against the computer on the ELEGOO 2.8" TFT touch
 * screen shield.
 *
 * <p>Tap one of the five buttons along the bottom. Each move beats two others and loses to the
 * other two: scissors cuts paper, paper covers rock, rock crushes lizard, lizard poisons Spock,
 * Spock smashes scissors, scissors decapitates lizard, lizard eats paper, paper disproves Spock,
 * Spock vaporizes rock, and — as it always has — rock crushes scissors. The rule that decided the
 * round lights up in the list above the buttons, and the header keeps the score.
 *
 * <p>The computer is not purely random: it remembers which move you tend to play after each of
 * your moves, predicts your next one, and plays one of the two moves that beat it, mixing in
 * random moves a third of the time so that it cannot be exploited in turn. Try to be unpredictable.
 *
 * <p>Each move has a 20x20 pixel-art icon stored as a 400-character string (one character per
 * pixel, mapped to a color by {@link #paletteColor}), streamed at 4x for the duel and 2x on the
 * buttons.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class RockPaperScissorsLizardSpock {
    private static final int MOVES = 5;
    private static final int ROCK_MOVE = 0;
    private static final int PAPER_MOVE = 1;
    private static final int SCISSORS_MOVE = 2;
    private static final int LIZARD_MOVE = 3;
    private static final int SPOCK_MOVE = 4;
    private static final int ICON = 20;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEADER_HEIGHT = 22;
    private static final int DUEL_Y = 44;
    private static final int YOU_X = 16;
    private static final int CPU_X = 144;
    private static final int VERB_Y = 138;
    private static final int OUTCOME_Y = 152;
    private static final int RULES_Y = 184;
    private static final int BUTTON_Y = 250;
    private static final int BUTTON_WIDTH = 48;
    private static final int BUTTON_HEIGHT = 68;

    private static final int BACKGROUND = 0x10A6;
    private static final int PANEL = 0x2128;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON = 0x3A0E;
    private static final int BUTTON_PRESSED = 0x6B7F;
    private static final int RULE = 0x8C71;
    private static final int RULE_LIT = TftTouchShield.YELLOW;

    private static int humanWins;
    private static int computerWins;
    private static int ties;
    private static int previous = -1;
    private static boolean seeded;

    // The icons: '.' is transparent; see paletteColor for the others.
    private static final String ROCK = ""
            + "...................."
            + "...................."
            + "........kkkkk......."
            + "......kkgggggkk....."
            + ".....kgwwggggggk...."
            + "....kgwwwgggggGGk..."
            + "...kgwwgggggggGGk..."
            + "..kggwgggggGgggGGk.."
            + "..kgggggggGGggggGk.."
            + ".kggggggggggggGGGGk."
            + ".kgggGggggggggGGGGk."
            + ".kggGGgggggggGGGGGk."
            + "kgggggggggggGGGGGGGk"
            + "kGggggggggGGGGGGGGGk"
            + "kGGgggggggGGGGGGGGGk"
            + ".kGGGgggGGGGGGGGGGk."
            + "..kkGGGGGGGGGGGGkk.."
            + "....kkkkkkkkkkkk...."
            + "...................."
            + "....................";
    private static final String PAPER = ""
            + "...................."
            + "...kkkkkkkkkkk......"
            + "...kWWWWWWWWWkk....."
            + "...kWWWWWWWWWkPk...."
            + "...kWbbbbbbbWkPPk..."
            + "...kWWWWWWWWWkkkkk.."
            + "...kWbbbbbbbbbbbWk.."
            + "...kWWWWWWWWWWWWWk.."
            + "...kWbbbbbbbbbbbWk.."
            + "...kWWWWWWWWWWWWWk.."
            + "...kWbbbbbbbbbbbWk.."
            + "...kWWWWWWWWWWWWWk.."
            + "...kWbbbbbbbbbbbWk.."
            + "...kWWWWWWWWWWWWWk.."
            + "...kWbbbbbbbbWWWWk.."
            + "...kWWWWWWWWWWWWWk.."
            + "...kpWWWWWWWWWWWpk.."
            + "...kpppppppppppppk.."
            + "...kkkkkkkkkkkkkkk.."
            + "....................";
    private static final String SCISSORS = ""
            + ".kk..............kk."
            + ".kxk............kxk."
            + "..ksk..........ksk.."
            + "..kxsk........ksxk.."
            + "...ksSk......kSsk..."
            + "....ksSk....kSsk...."
            + ".....ksSk..kSsk....."
            + "......ksSkkSsk......"
            + ".......ksSSsk......."
            + "........kSSk........"
            + ".......kRkkRk......."
            + "......kRk..kRk......"
            + "...kkkRk....kRkkk..."
            + "..krrrrk....krrrrk.."
            + ".krk..rk....kr..krk."
            + ".krk..rk....kr..krk."
            + ".krk..rk....kr..krk."
            + "..krrrrk....krrrrk.."
            + "...kkkk......kkkk..."
            + "....................";
    private static final String LIZARD = ""
            + "........knnk........"
            + ".......knnnnk......."
            + "...k...kynnyk...k..."
            + "..kNkk.knnnnk.kkNk.."
            + "...knnkknnnnkknnk..."
            + "...knnkkNnnnkknnk..."
            + "....knnnNllnnnnk...."
            + ".....kkkNllnkkk....."
            + ".......kNllnk......."
            + ".......kNllnk......."
            + ".....kkkNllnkkk....."
            + "....knnnNllnnnnk...."
            + "...knnkkNnnnkknnk..."
            + "...knnk.knnk.knnk..."
            + "..kNkk..knnk..kkNk.."
            + "...k.....kNNk.k.k..."
            + ".........kNNkkNk...."
            + "..........kkNNk....."
            + "............kk......"
            + "....................";
    private static final String SPOCK = ""
            + "..........kk........"
            + ".....kk..kffk......."
            + "....kffk.kfhkkk....."
            + "....kfhk.kfhkffk...."
            + "..kkkfhk.kfhkffk...."
            + ".kffkfhk.kfhkffk...."
            + ".kffkfhkkkfhkffk...."
            + ".kffkfhkfkfhkffkkk.."
            + ".kffkfhffffhkffkffk."
            + ".kffkfhffffhkffkffk."
            + ".kfffffffffffffkffk."
            + ".kFfffffffffffffffk."
            + ".kFffffffffffffffk.."
            + ".kFffffffffffffffk.."
            + ".kFffffffffffffffk.."
            + ".kFFFFFFFFFFFFfkk..."
            + "..kFFFFFFFFFFFk....."
            + "..kFFFFFFFFFFFk....."
            + "...kkkkkkkkkkk......"
            + "....................";

    private RockPaperScissorsLizardSpock() {
    }

    public static void main(String[] args) {
        // habits[a * 5 + b]: how often you played b right after a.
        int[] habits = new int[MOVES * MOVES];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(BACKGROUND);
        drawHeader();
        drawDuelFrames();
        drawRules(-1, -1);
        for (int move = 0; move < MOVES; move++) {
            drawButton(move, false);
        }
        showOutcome("Your move!", TftTouchShield.WHITE);

        while (true) {
            int human = waitForMove();
            if (!seeded) {
                Random.seed(Clock.micros());
                seeded = true;
            }
            drawButton(human, true);
            int computer = chooseMove(habits);
            play(human, computer);
            drawButton(human, false);
            if (previous >= 0) {
                habits[previous * MOVES + human] = habits[previous * MOVES + human] + 1;
            }
            previous = human;
        }
    }

    // ---- Game flow ----

    private static void play(int human, int computer) {
        showVerb("");
        showOutcome("", TftTouchShield.WHITE);
        drawIcon(icon(human), YOU_X, DUEL_Y, 4, PANEL);
        // Suspense: the computer's side flickers through the moves before settling.
        for (int frame = 0; frame < 10; frame++) {
            drawIcon(icon(frame % MOVES), CPU_X, DUEL_Y, 4, PANEL);
            Delay.millis(60 + frame * 12);
        }
        drawIcon(icon(computer), CPU_X, DUEL_Y, 4, PANEL);
        drawName(human, YOU_X);
        drawName(computer, CPU_X);
        if (human == computer) {
            ties = ties + 1;
            showVerb("Same move");
            showOutcome("TIE", TftTouchShield.WHITE);
            drawRules(-1, -1);
        } else if (beats(human, computer)) {
            humanWins = humanWins + 1;
            showRule(human, computer);
            showOutcome("YOU WIN!", TftTouchShield.GREEN);
            drawRules(human, computer);
        } else {
            computerWins = computerWins + 1;
            showRule(computer, human);
            showOutcome("CPU WINS", TftTouchShield.RED);
            drawRules(computer, human);
        }
        drawHeader();
    }

    /** Predicts your next move from your habits and answers it, a third of the time at random. */
    private static int chooseMove(int[] habits) {
        if (previous < 0 || Random.nextInt(3) == 0) {
            return Random.nextInt(MOVES);
        }
        int predicted = -1;
        int most = 0;
        int start = Random.nextInt(MOVES);
        for (int i = 0; i < MOVES; i++) {
            int move = (start + i) % MOVES;
            int count = habits[previous * MOVES + move];
            if (count > most) {
                most = count;
                predicted = move;
            }
        }
        if (predicted < 0) {
            return Random.nextInt(MOVES);
        }
        // Two moves beat any move; pick one of them.
        int pick = Random.nextInt(2);
        for (int move = 0; move < MOVES; move++) {
            if (beats(move, predicted)) {
                if (pick == 0) {
                    return move;
                }
                pick = pick - 1;
            }
        }
        return Random.nextInt(MOVES);
    }

    // ---- Rules ----

    private static boolean beats(int a, int b) {
        return verb(a, b).length() > 0;
    }

    /** How {@code a} beats {@code b}, or "" when it does not. */
    private static String verb(int a, int b) {
        if (a == SCISSORS_MOVE && b == PAPER_MOVE) {
            return "cuts";
        }
        if (a == PAPER_MOVE && b == ROCK_MOVE) {
            return "covers";
        }
        if (a == ROCK_MOVE && b == LIZARD_MOVE) {
            return "crushes";
        }
        if (a == LIZARD_MOVE && b == SPOCK_MOVE) {
            return "poisons";
        }
        if (a == SPOCK_MOVE && b == SCISSORS_MOVE) {
            return "smashes";
        }
        if (a == SCISSORS_MOVE && b == LIZARD_MOVE) {
            return "decapitates";
        }
        if (a == LIZARD_MOVE && b == PAPER_MOVE) {
            return "eats";
        }
        if (a == PAPER_MOVE && b == SPOCK_MOVE) {
            return "disproves";
        }
        if (a == SPOCK_MOVE && b == ROCK_MOVE) {
            return "vaporizes";
        }
        if (a == ROCK_MOVE && b == SCISSORS_MOVE) {
            return "crushes";
        }
        return "";
    }

    private static String name(int move) {
        if (move == ROCK_MOVE) {
            return "Rock";
        }
        if (move == PAPER_MOVE) {
            return "Paper";
        }
        if (move == SCISSORS_MOVE) {
            return "Scissors";
        }
        if (move == LIZARD_MOVE) {
            return "Lizard";
        }
        return "Spock";
    }

    private static String icon(int move) {
        if (move == ROCK_MOVE) {
            return ROCK;
        }
        if (move == PAPER_MOVE) {
            return PAPER;
        }
        if (move == SCISSORS_MOVE) {
            return SCISSORS;
        }
        if (move == LIZARD_MOVE) {
            return LIZARD;
        }
        return SPOCK;
    }

    // ---- Input ----

    private static int waitForMove() {
        while (true) {
            if (TftTouchShield.readTouch()) {
                int x = TftTouchShield.touchX();
                int y = TftTouchShield.touchY();
                waitForRelease();
                if (y >= BUTTON_Y) {
                    return Math.min(MOVES - 1, x / BUTTON_WIDTH);
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

    /** Streams a 20x20 icon at {@code scale}, with {@code background} behind transparent pixels. */
    private static void drawIcon(String icon, int x, int y, int scale, int background) {
        int size = ICON * scale;
        if (!TftTouchShield.beginPixels(x, y, size, size)) {
            return;
        }
        for (int row = 0; row < ICON; row++) {
            for (int repeatRow = 0; repeatRow < scale; repeatRow++) {
                for (int column = 0; column < ICON; column++) {
                    int color = paletteColor(icon.charAt(row * ICON + column), background);
                    for (int repeatColumn = 0; repeatColumn < scale; repeatColumn++) {
                        TftTouchShield.pushPixel(color);
                    }
                }
            }
        }
    }

    private static int paletteColor(char c, int background) {
        if (c == 'k') {
            return 0x2104;
        }
        if (c == 'g') {
            return 0x8C51;
        }
        if (c == 'G') {
            return 0x52AA;
        }
        if (c == 'w') {
            return 0xC638;
        }
        if (c == 'W') {
            return 0xFFFF;
        }
        if (c == 'p') {
            return 0xDEFB;
        }
        if (c == 'b') {
            return 0x7D7F;
        }
        if (c == 'P') {
            return 0xAD55;
        }
        if (c == 's') {
            return 0xC618;
        }
        if (c == 'S') {
            return 0x7BCF;
        }
        if (c == 'x') {
            return 0xEF7D;
        }
        if (c == 'r') {
            return 0xE8A4;
        }
        if (c == 'R') {
            return 0xA000;
        }
        if (c == 'n') {
            return 0x3E66;
        }
        if (c == 'N') {
            return 0x1C62;
        }
        if (c == 'l') {
            return 0x9F2E;
        }
        if (c == 'y') {
            return 0xFFE0;
        }
        if (c == 'f') {
            return 0xFDB4;
        }
        if (c == 'F') {
            return 0xD46E;
        }
        if (c == 'h') {
            return 0xFED8;
        }
        return background;
    }

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, HEADER_BACKGROUND);
        TftTouchShield.setCursor(6, 4);
        TftTouchShield.print("You ");
        TftTouchShield.print(humanWins);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, HEADER_BACKGROUND);
        TftTouchShield.setCursor(100, 8);
        TftTouchShield.print("Ties ");
        TftTouchShield.print(ties);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.ORANGE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(150, 4);
        TftTouchShield.print("CPU ");
        TftTouchShield.print(computerWins);
    }

    private static void drawDuelFrames() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, BACKGROUND);
        TftTouchShield.setCursor(YOU_X + 31, DUEL_Y - 12);
        TftTouchShield.print("YOU");
        TftTouchShield.setTextColor(TftTouchShield.ORANGE, BACKGROUND);
        TftTouchShield.setCursor(CPU_X + 31, DUEL_Y - 12);
        TftTouchShield.print("CPU");
        TftTouchShield.fillRect(YOU_X, DUEL_Y, 80, 80, PANEL);
        TftTouchShield.fillRect(CPU_X, DUEL_Y, 80, 80, PANEL);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BACKGROUND);
        TftTouchShield.setCursor(108, DUEL_Y + 32);
        TftTouchShield.print("VS");
    }

    private static void drawName(int move, int x) {
        TftTouchShield.fillRect(x, DUEL_Y + 82, 80, 10, BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BACKGROUND);
        String text = name(move);
        TftTouchShield.setCursor(x + (80 - text.length() * 6) / 2, DUEL_Y + 83);
        TftTouchShield.print(text);
    }

    /** "Spock vaporizes Rock", centered. */
    private static void showRule(int winner, int loser) {
        String first = name(winner);
        String middle = verb(winner, loser);
        String last = name(loser);
        int length = first.length() + middle.length() + last.length() + 2;
        TftTouchShield.fillRect(0, VERB_Y, WIDTH, 10, BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, BACKGROUND);
        TftTouchShield.setCursor((WIDTH - length * 6) / 2, VERB_Y + 1);
        TftTouchShield.print(first);
        TftTouchShield.print(" ");
        TftTouchShield.print(middle);
        TftTouchShield.print(" ");
        TftTouchShield.print(last);
    }

    private static void showVerb(String text) {
        TftTouchShield.fillRect(0, VERB_Y, WIDTH, 10, BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, BACKGROUND);
        TftTouchShield.setCursor((WIDTH - text.length() * 6) / 2, VERB_Y + 1);
        TftTouchShield.print(text);
    }

    private static void showOutcome(String text, int color) {
        TftTouchShield.fillRect(0, OUTCOME_Y, WIDTH, 26, BACKGROUND);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(color, BACKGROUND);
        TftTouchShield.setCursor((WIDTH - text.length() * 18) / 2, OUTCOME_Y + 2);
        TftTouchShield.print(text);
    }

    /** The ten rules, one line per move ("Rock crushes Lizard, crushes Scissors"); the winner's line is lit. */
    private static void drawRules(int winner, int loser) {
        TftTouchShield.fillRect(0, RULES_Y - 4, WIDTH, 62, PANEL);
        TftTouchShield.setTextSize(1);
        for (int a = 0; a < MOVES; a++) {
            int color = RULE;
            if (a == winner) {
                color = RULE_LIT;
            }
            TftTouchShield.setTextColor(color, PANEL);
            TftTouchShield.setCursor(4, RULES_Y + a * 11);
            TftTouchShield.print(name(a));
            boolean first = true;
            for (int b = 0; b < MOVES; b++) {
                if (beats(a, b)) {
                    if (!first) {
                        TftTouchShield.print(",");
                    }
                    TftTouchShield.print(" ");
                    TftTouchShield.print(verb(a, b));
                    TftTouchShield.print(" ");
                    TftTouchShield.print(name(b));
                    first = false;
                }
            }
        }
    }

    private static void drawButton(int move, boolean pressed) {
        int x = move * BUTTON_WIDTH;
        int color = BUTTON;
        if (pressed) {
            color = BUTTON_PRESSED;
        }
        TftTouchShield.fillRect(x + 1, BUTTON_Y, BUTTON_WIDTH - 2, BUTTON_HEIGHT, color);
        drawIcon(icon(move), x + 4, BUTTON_Y + 4, 2, color);
        String text = name(move);
        if (move == SCISSORS_MOVE) {
            text = "Sciss.";
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (BUTTON_WIDTH - text.length() * 6) / 2, BUTTON_Y + 52);
        TftTouchShield.print(text);
    }
}
