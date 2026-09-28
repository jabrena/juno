package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Backgammon against the computer on the ELEGOO 2.8" TFT touch screen shield, in landscape. You
 * play white, moving from the top right round to your home board at the bottom right; the computer
 * plays black the opposite way.
 *
 * <p>Each game opens with both players rolling one die: the higher roll moves first, using both
 * dice. Then tap {@code ROLL}, tap a checker (or the bar) and then one of the rings that mark
 * where it may go, one die at a time; to bear off, tap your tray at the bottom right. {@code UNDO}
 * takes back this turn's moves, and {@code DONE} ends the turn once no die can be used. The full
 * rules apply: checkers on the bar must enter first, a single checker (a blot) can be hit, you
 * must use both dice when possible (and the higher one when only one can be used), doubles are
 * played four times, and bearing off needs all fifteen checkers home. A win scores 1 point, a
 * gammon 2 and a backgammon 3; the header keeps the running score. There is no doubling cube.
 *
 * <p>The computer tries every complete play of its roll and keeps the one whose final position
 * scores best: pip count, blocks and primes, exposed blots weighted by how many enemy checkers can
 * reach them, and checkers on the bar; once the armies have passed each other it plays a pure race.
 *
 * <p>The board is 28 bytes: points 1-24 hold a signed count (positive for white, negative for
 * black), byte 25 is white's bar, byte 0 black's bar, and bytes 26 and 27 the checkers each side
 * has borne off. White moves towards point 1 and black towards point 24, so a move of {@code die}
 * from {@code from} lands on {@code from - side * die} for both.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Backgammon {
    private static final int SIZE = 28;
    private static final int LEVELS = 6;
    private static final int HUMAN = 1;
    private static final int COMPUTER = -1;
    private static final int HUMAN_BAR = 25;
    private static final int COMPUTER_BAR = 0;
    private static final int HUMAN_OFF = 26;
    private static final int COMPUTER_OFF = 27;
    /** Target meaning "borne off". */
    private static final int OFF = 100;
    private static final int CHECKERS = 15;
    private static final int INFINITY = 1000000;

    // Phases.
    private static final int OPENING = 0;
    private static final int ROLLING = 1;
    private static final int MOVING = 2;
    private static final int GAME_OVER = 3;
    private static final int COMPUTER_TURN = 4;

    // Layout (landscape, 320x240).
    private static final int WIDTH = 320;
    private static final int HEADER_HEIGHT = 18;
    private static final int TOP = 20;
    private static final int BOTTOM = 238;
    private static final int POINT_HEIGHT = 90;
    private static final int POINT_WIDTH = 20;
    private static final int BAR_X = 120;
    private static final int BAR_WIDTH = 24;
    private static final int RIGHT_X = 144;
    private static final int BOARD_WIDTH = 264;
    private static final int CHECKER = 9;
    private static final int STEP = 18;
    private static final int MIDDLE_Y = 129;
    private static final int PANEL_X = 268;
    private static final int PANEL_WIDTH = 50;
    private static final int COMPUTER_TRAY_Y = 20;
    private static final int HUMAN_TRAY_Y = 168;
    private static final int TRAY_HEIGHT = 70;
    private static final int BUTTON_Y = 96;
    private static final int BUTTON_HEIGHT = 30;
    private static final int NEW_X = 276;
    private static final int DIE = 20;

    private static final int FELT = 0x2B45;
    private static final int FRAME = 0x6A22;
    private static final int LIGHT_POINT = 0xE6B5;
    private static final int DARK_POINT = 0xB0A3;
    private static final int WHITE_CHECKER = 0xFFDE;
    private static final int BLACK_CHECKER = 0x2104;
    private static final int MARK = TftTouchShield.YELLOW;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON = 0xCD05;
    private static final int BUTTON_DISABLED = 0x5ACB;

    private static int phase;
    private static int diceCount;
    private static int usedMask;
    private static int movesLeft;
    private static int selected;
    private static int humanScore;
    private static int computerScore;
    private static int bestScore;
    private static int bestLength;

    private Backgammon() {
    }

    public static void main(String[] args) {
        // Level 0 is the real board; levels 1.. are scratch copies for move searches.
        byte[] board = new byte[SIZE * LEVELS];
        byte[] turnStart = new byte[SIZE];
        int[] dice = new int[4];
        // Current and best move sequences found by the computer: from, then target, per move.
        int[] path = new int[8];
        int[] best = new int[8];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        newGame(board, dice);

        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            waitForRelease();
            if (y < HEADER_HEIGHT && x >= NEW_X) {
                humanScore = 0;
                computerScore = 0;
                newGame(board, dice);
            } else if (x >= PANEL_X && y >= BUTTON_Y && y < BUTTON_Y + BUTTON_HEIGHT) {
                pressMain(board, turnStart, dice, path, best);
            } else if (x >= PANEL_X && y >= BUTTON_Y + BUTTON_HEIGHT + 6 && y < HUMAN_TRAY_Y - 4) {
                if (phase == MOVING) {
                    undo(board, turnStart, dice);
                }
            } else if (phase == MOVING) {
                tapBoard(board, dice, targetAt(x, y));
            }
        }
    }

    // ---- Game flow ----

    private static void newGame(byte[] board, int[] dice) {
        for (int i = 0; i < SIZE; i++) {
            board[i] = 0;
        }
        board[24] = 2;
        board[13] = 5;
        board[8] = 3;
        board[6] = 5;
        board[1] = -2;
        board[12] = -5;
        board[17] = -3;
        board[19] = -5;
        phase = OPENING;
        diceCount = 0;
        selected = -1;
        drawAll(board, dice);
        showMessage("Roll to start");
        drawButtons();
    }

    /** The ROLL / DONE / PLAY button, depending on the phase. */
    private static void pressMain(byte[] board, byte[] turnStart, int[] dice, int[] path, int[] best) {
        if (phase == GAME_OVER) {
            newGame(board, dice);
            return;
        }
        if (phase == OPENING) {
            Random.seed(Clock.micros());
            int mine = 0;
            int theirs = 0;
            while (mine == theirs) {
                mine = Random.nextInt(1, 7);
                theirs = Random.nextInt(1, 7);
            }
            dice[0] = Math.max(mine, theirs);
            dice[1] = Math.min(mine, theirs);
            diceCount = 2;
            usedMask = 0;
            drawDie(RIGHT_X + 10, mine, true);
            drawDie(BAR_X - DIE - 4, theirs, true);
            if (mine > theirs) {
                showMessage("You start");
                Delay.millis(1200);
                startHumanMoves(board, turnStart, dice);
            } else {
                showMessage("CPU starts");
                Delay.millis(1200);
                computerMoves(board, dice, path, best);
            }
            return;
        }
        if (phase == ROLLING) {
            roll(dice);
            startHumanMoves(board, turnStart, dice);
            return;
        }
        if (phase == MOVING && movesLeft == 0) {
            selected = -1;
            if (!checkWin(board, dice)) {
                roll(dice);
                computerMoves(board, dice, path, best);
            }
        }
    }

    private static void roll(int[] dice) {
        int a = Random.nextInt(1, 7);
        int b = Random.nextInt(1, 7);
        if (a == b) {
            diceCount = 4;
            for (int i = 0; i < 4; i++) {
                dice[i] = a;
            }
        } else {
            diceCount = 2;
            dice[0] = Math.max(a, b);
            dice[1] = Math.min(a, b);
        }
        usedMask = 0;
    }

    private static void startHumanMoves(byte[] board, byte[] turnStart, int[] dice) {
        for (int i = 0; i < SIZE; i++) {
            turnStart[i] = board[i];
        }
        phase = MOVING;
        selected = -1;
        movesLeft = maxMoves(board, 0, dice, usedMask, HUMAN);
        drawMiddle(board, dice);
        if (movesLeft == 0) {
            showMessage("No moves");
        } else {
            showMessage("Your move");
        }
        drawButtons();
    }

    private static void undo(byte[] board, byte[] turnStart, int[] dice) {
        for (int i = 0; i < SIZE; i++) {
            board[i] = turnStart[i];
        }
        usedMask = 0;
        selected = -1;
        movesLeft = maxMoves(board, 0, dice, 0, HUMAN);
        drawAll(board, dice);
        showMessage("Your move");
        drawButtons();
    }

    /** Handles a tap on a point, the bar, or the bear-off tray while it is your move. */
    private static void tapBoard(byte[] board, int[] dice, int target) {
        if (target < 0 || movesLeft == 0) {
            return;
        }
        if (selected >= 0) {
            int die = dieFor(board, dice, selected, target);
            if (die >= 0) {
                int from = selected;
                select(board, dice, -1);
                applyMove(board, 0, from, target, HUMAN);
                usedMask = usedMask | (1 << die);
                movesLeft = maxMoves(board, 0, dice, usedMask, HUMAN);
                drawAll(board, dice);
                if (board[HUMAN_OFF] == CHECKERS) {
                    checkWin(board, dice);
                    return;
                }
                if (movesLeft == 0) {
                    showMessage("Tap DONE");
                }
                drawButtons();
                return;
            }
        }
        if (target != OFF && target != selected && hasMoveFrom(board, dice, target)) {
            select(board, dice, target);
        } else {
            select(board, dice, -1);
        }
    }

    /** Plays the computer's roll: finds its best complete play and animates it move by move. */
    private static void computerMoves(byte[] board, int[] dice, int[] path, int[] best) {
        phase = COMPUTER_TURN;
        drawMiddle(board, dice);
        showMessage("CPU thinking");
        drawButtons();
        int length = maxMoves(board, 0, dice, 0, COMPUTER);
        if (length == 0) {
            showMessage("CPU can't move");
            Delay.millis(1200);
        } else {
            bestScore = -INFINITY;
            bestLength = length;
            boolean higherOnly = length == 1 && diceCount == 2
                    && canUseDie(board, dice, 0, COMPUTER);
            findBest(board, 0, dice, 0, -1, COMPUTER, path, best, higherOnly);
            Delay.millis(500);
            usedMask = 0;
            for (int i = 0; i < length; i++) {
                applyMove(board, 0, best[2 * i], best[2 * i + 1], COMPUTER);
                useDieFor(dice, best[2 * i], best[2 * i + 1]);
                drawAll(board, dice);
                Delay.millis(600);
            }
        }
        if (checkWin(board, dice)) {
            return;
        }
        diceCount = 0;
        phase = ROLLING;
        drawMiddle(board, dice);
        showMessage("Your roll");
        drawButtons();
    }

    /** Marks the die the computer's move used, for the dice display. */
    private static void useDieFor(int[] dice, int from, int target) {
        int distance = target - from;
        for (int k = 0; k < diceCount; k++) {
            if ((usedMask & (1 << k)) == 0 && (dice[k] == distance || (target == OFF && dice[k] >= 25 - from))) {
                usedMask = usedMask | (1 << k);
                return;
            }
        }
    }

    private static boolean checkWin(byte[] board, int[] dice) {
        int winner = 0;
        if (board[HUMAN_OFF] == CHECKERS) {
            winner = HUMAN;
        } else if (board[COMPUTER_OFF] == CHECKERS) {
            winner = COMPUTER;
        }
        if (winner == 0) {
            return false;
        }
        int points = 1;
        int loserOff = COMPUTER_OFF;
        if (winner == COMPUTER) {
            loserOff = HUMAN_OFF;
        }
        if (board[loserOff] == 0) {
            points = 2;
            // Backgammon: the loser still has a checker on the bar or in the winner's home board.
            for (int p = 0; p <= 25; p++) {
                if (count(board, 0, p, -winner) > 0 && (p == barOf(-winner) || isHome(p, winner))) {
                    points = 3;
                }
            }
        }
        if (winner == HUMAN) {
            humanScore = humanScore + points;
        } else {
            computerScore = computerScore + points;
        }
        phase = GAME_OVER;
        diceCount = 0;
        drawAll(board, dice);
        if (winner == HUMAN) {
            if (points == 3) {
                showMessage("Backgammon!");
            } else if (points == 2) {
                showMessage("Gammon!");
            } else {
                showMessage("You win!");
            }
        } else if (points == 3) {
            showMessage("CPU backgammon");
        } else if (points == 2) {
            showMessage("CPU gammon");
        } else {
            showMessage("CPU wins");
        }
        drawButtons();
        return true;
    }

    // ---- Rules (on the board copy at offset o) ----

    private static int barOf(int side) {
        if (side == HUMAN) {
            return HUMAN_BAR;
        }
        return COMPUTER_BAR;
    }

    private static int offOf(int side) {
        if (side == HUMAN) {
            return HUMAN_OFF;
        }
        return COMPUTER_OFF;
    }

    /** Whether point {@code p} is in {@code side}'s home board. */
    private static boolean isHome(int p, int side) {
        if (side == HUMAN) {
            return p >= 1 && p <= 6;
        }
        return p >= 19 && p <= 24;
    }

    /** How many of {@code side}'s checkers are on point or bar {@code p}. */
    private static int count(byte[] b, int o, int p, int side) {
        if (p == HUMAN_BAR || p == COMPUTER_BAR) {
            if (p == barOf(side)) {
                return b[o + p];
            }
            return 0;
        }
        int n = b[o + p] * side;
        if (n > 0) {
            return n;
        }
        return 0;
    }

    private static boolean allHome(byte[] b, int o, int side) {
        if (b[o + barOf(side)] > 0) {
            return false;
        }
        for (int p = 1; p <= 24; p++) {
            if (!isHome(p, side) && count(b, o, p, side) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where a checker of {@code side} on {@code from} lands with {@code die}: a point, {@link #OFF},
     * or -1 when that is not a legal single move.
     */
    private static int target(byte[] b, int o, int from, int die, int side) {
        int bar = barOf(side);
        if (b[o + bar] > 0 && from != bar) {
            return -1;
        }
        if (count(b, o, from, side) == 0) {
            return -1;
        }
        int to = from - side * die;
        if (to >= 1 && to <= 24) {
            if (b[o + to] * side < -1) {
                return -1;
            }
            return to;
        }
        if (!allHome(b, o, side)) {
            return -1;
        }
        if (to == 0 || to == 25) {
            return OFF;
        }
        // Overshooting the edge is allowed only from the rearmost checker.
        if (side == HUMAN) {
            for (int p = from + 1; p <= 6; p++) {
                if (count(b, o, p, side) > 0) {
                    return -1;
                }
            }
        } else {
            for (int p = 19; p < from; p++) {
                if (count(b, o, p, side) > 0) {
                    return -1;
                }
            }
        }
        return OFF;
    }

    private static void applyMove(byte[] b, int o, int from, int to, int side) {
        if (from == barOf(side)) {
            b[o + from] = (byte) (b[o + from] - 1);
        } else {
            b[o + from] = (byte) (b[o + from] - side);
        }
        if (to == OFF) {
            b[o + offOf(side)] = (byte) (b[o + offOf(side)] + 1);
            return;
        }
        if (b[o + to] * side == -1) {
            b[o + to] = 0;
            b[o + barOf(-side)] = (byte) (b[o + barOf(-side)] + 1);
        }
        b[o + to] = (byte) (b[o + to] + side);
    }

    /** The first source to try: the bar, then the points in the order {@code side} passes them. */
    private static int source(int index, int side) {
        if (side == HUMAN) {
            return HUMAN_BAR - index;
        }
        return index;
    }

    /**
     * The most dice {@code side} can still use from the board at level {@code level}, given the
     * dice already used in {@code used}; stops early once every die is known to be usable.
     */
    private static int maxMoves(byte[] b, int level, int[] dice, int used, int side) {
        int remaining = 0;
        for (int k = 0; k < diceCount; k++) {
            if ((used & (1 << k)) == 0) {
                remaining = remaining + 1;
            }
        }
        if (remaining == 0 || level >= LEVELS - 1) {
            return 0;
        }
        int o = level * SIZE;
        int best = 0;
        for (int k = 0; k < diceCount; k++) {
            if ((used & (1 << k)) != 0 || (k > 0 && dice[k] == dice[k - 1] && (used & (1 << (k - 1))) == 0)) {
                continue;
            }
            for (int index = 0; index <= 25; index++) {
                int from = source(index, side);
                int to = target(b, o, from, dice[k], side);
                if (to < 0) {
                    continue;
                }
                copyLevel(b, level);
                applyMove(b, o + SIZE, from, to, side);
                int moves = 1 + maxMoves(b, level + 1, dice, used | (1 << k), side);
                if (moves > best) {
                    best = moves;
                    if (best == remaining) {
                        return best;
                    }
                }
            }
        }
        return best;
    }

    private static void copyLevel(byte[] b, int level) {
        int o = level * SIZE;
        for (int i = 0; i < SIZE; i++) {
            b[o + SIZE + i] = b[o + i];
        }
    }

    private static boolean canUseDie(byte[] b, int[] dice, int k, int side) {
        for (int index = 0; index <= 25; index++) {
            if (target(b, 0, source(index, side), dice[k], side) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether using die {@code k} from {@code from} keeps the most dice usable, as the rules demand. */
    private static boolean legalHumanMove(byte[] board, int[] dice, int from, int k) {
        if ((usedMask & (1 << k)) != 0) {
            return false;
        }
        int to = target(board, 0, from, dice[k], HUMAN);
        if (to < 0) {
            return false;
        }
        copyLevel(board, 0);
        applyMove(board, SIZE, from, to, HUMAN);
        if (1 + maxMoves(board, 1, dice, usedMask | (1 << k), HUMAN) != movesLeft) {
            return false;
        }
        // Only one die can be played: it must be the higher one when that is possible.
        if (movesLeft == 1 && diceCount == 2 && usedMask == 0 && k == 1 && dice[0] != dice[1]) {
            return !canUseDie(board, dice, 0, HUMAN);
        }
        return true;
    }

    private static boolean hasMoveFrom(byte[] board, int[] dice, int from) {
        for (int k = 0; k < diceCount; k++) {
            if (legalHumanMove(board, dice, from, k)) {
                return true;
            }
        }
        return false;
    }

    /** The die that moves {@code from} to {@code to} legally (the smallest, when bearing off), or -1. */
    private static int dieFor(byte[] board, int[] dice, int from, int to) {
        int chosen = -1;
        for (int k = 0; k < diceCount; k++) {
            if (target(board, 0, from, dice[k], HUMAN) == to && legalHumanMove(board, dice, from, k)) {
                if (chosen < 0 || dice[k] < dice[chosen]) {
                    chosen = k;
                }
            }
        }
        return chosen;
    }

    // ---- Computer player ----

    /**
     * Tries every play of {@link #bestLength} moves and keeps the best-scoring one in {@code best}.
     * With doubles the four moves are interchangeable, so they are only tried in the order the
     * checkers travel ({@code last} is the previous source), which skips reorderings of the same play.
     */
    private static void findBest(byte[] b, int level, int[] dice, int used, int last, int side, int[] path,
                                 int[] best, boolean higherOnly) {
        if (level == bestLength) {
            int score = evaluate(b, level * SIZE, side);
            if (score > bestScore) {
                bestScore = score;
                for (int i = 0; i < 2 * bestLength; i++) {
                    best[i] = path[i];
                }
            }
            return;
        }
        int o = level * SIZE;
        boolean doubles = diceCount == 4;
        for (int k = 0; k < diceCount; k++) {
            if ((used & (1 << k)) != 0 || (k > 0 && dice[k] == dice[k - 1] && (used & (1 << (k - 1))) == 0)) {
                continue;
            }
            if (higherOnly && k != 0) {
                continue;
            }
            for (int index = 0; index <= 25; index++) {
                int from = source(index, side);
                if (doubles && last >= 0 && index < last) {
                    continue;
                }
                int to = target(b, o, from, dice[k], side);
                if (to < 0) {
                    continue;
                }
                copyLevel(b, level);
                applyMove(b, o + SIZE, from, to, side);
                path[2 * level] = from;
                path[2 * level + 1] = to;
                findBest(b, level + 1, dice, used | (1 << k), index, side, path, best, higherOnly);
            }
        }
    }

    /** Pips {@code side} still has to travel: its bar counts 25, borne-off checkers 0. */
    private static int pips(byte[] b, int o, int side) {
        int total = count(b, o, barOf(side), side) * 25;
        for (int p = 1; p <= 24; p++) {
            int distance = p;
            if (side == COMPUTER) {
                distance = 25 - p;
            }
            total = total + count(b, o, p, side) * distance;
        }
        return total;
    }

    /** Heuristic value of the position for {@code side}. */
    private static int evaluate(byte[] b, int o, int side) {
        int mine = pips(b, o, side);
        int theirs = pips(b, o, -side);
        if (b[o + offOf(side)] == CHECKERS) {
            return INFINITY / 2;
        }
        if (!contact(b, o)) {
            return (theirs - mine) * 10 + b[o + offOf(side)] * 5;
        }
        int score = (theirs - mine) * 4;
        score = score + b[o + barOf(-side)] * 15 - b[o + barOf(side)] * 15;
        int run = 0;
        for (int step = 1; step <= 24; step++) {
            // Walk the points from this side's home outwards: 1..24 for white, 24..1 for black.
            int p = step;
            if (side == COMPUTER) {
                p = 25 - step;
            }
            int n = count(b, o, p, side);
            if (n >= 2) {
                score = score + 12;
                if (step <= 7) {
                    score = score + 8;
                }
                if (step <= 6 && b[o + barOf(-side)] > 0) {
                    score = score + 10;
                }
                run = run + 1;
                score = score + run * run * 4;
            } else {
                run = 0;
                if (n == 1) {
                    score = score - blotRisk(b, o, p, side, step);
                }
            }
        }
        return score;
    }

    /** Penalty for a lone checker on {@code p}: higher when enemy checkers are in direct range. */
    private static int blotRisk(byte[] b, int o, int p, int side, int step) {
        int nearest = 99;
        for (int distance = 1; distance <= 12; distance++) {
            // The enemy moves the other way, so it hits from points behind us on our own path.
            int q = p - side * distance;
            if (q <= 0 || q >= 25) {
                if (b[o + barOf(-side)] > 0) {
                    nearest = Math.min(nearest, distance);
                }
                break;
            }
            if (count(b, o, q, -side) > 0) {
                nearest = Math.min(nearest, distance);
            }
        }
        if (nearest <= 6) {
            return 20 + (25 - step) / 2;
        }
        if (nearest <= 12) {
            return 8;
        }
        return 0;
    }

    /** Whether some checker still has an enemy checker ahead of it (otherwise it is a pure race). */
    private static boolean contact(byte[] b, int o) {
        if (b[o + HUMAN_BAR] > 0 || b[o + COMPUTER_BAR] > 0) {
            return true;
        }
        int rearWhite = 0;
        int rearBlack = 25;
        for (int p = 1; p <= 24; p++) {
            if (b[o + p] > 0) {
                rearWhite = p;
            }
            if (b[o + 25 - p] < 0) {
                rearBlack = 25 - p;
            }
        }
        return rearWhite > rearBlack;
    }

    // ---- Input ----

    /** The point (1-24), your bar (25), or {@link #OFF} under a tap, or -1. */
    private static int targetAt(int x, int y) {
        if (x >= PANEL_X) {
            if (y >= HUMAN_TRAY_Y) {
                return OFF;
            }
            return -1;
        }
        if (y < TOP || x >= BOARD_WIDTH) {
            return -1;
        }
        if (x >= BAR_X && x < RIGHT_X) {
            return HUMAN_BAR;
        }
        int column = x / POINT_WIDTH;
        boolean top = y < MIDDLE_Y;
        if (x >= RIGHT_X) {
            column = (x - RIGHT_X) / POINT_WIDTH;
            if (top) {
                return 19 + column;
            }
            return 6 - column;
        }
        if (top) {
            return 13 + column;
        }
        return 12 - column;
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

    private static int pointX(int p) {
        if (p <= 6) {
            return RIGHT_X + (6 - p) * POINT_WIDTH;
        }
        if (p <= 12) {
            return (12 - p) * POINT_WIDTH;
        }
        if (p <= 18) {
            return (p - 13) * POINT_WIDTH;
        }
        return RIGHT_X + (p - 19) * POINT_WIDTH;
    }

    /** Center y of the {@code slot}-th checker (0 = at the edge) on point {@code p}. */
    private static int slotY(int p, int slot) {
        if (p >= 13) {
            return TOP + CHECKER + slot * STEP;
        }
        return BOTTOM - CHECKER - slot * STEP;
    }

    private static void drawAll(byte[] board, int[] dice) {
        drawHeader(board);
        TftTouchShield.fillRect(0, HEADER_HEIGHT, BOARD_WIDTH, BOTTOM - HEADER_HEIGHT + 2, FRAME);
        for (int p = 1; p <= 24; p++) {
            drawPoint(board, p, false);
        }
        drawBar(board);
        drawMiddle(board, dice);
        drawTray(board, COMPUTER);
        drawTray(board, HUMAN);
        if (selected >= 0) {
            select(board, dice, selected);
        }
    }

    private static void drawPoint(byte[] board, int p, boolean marked) {
        int x = pointX(p);
        boolean top = p >= 13;
        int y = TOP;
        if (!top) {
            y = BOTTOM - POINT_HEIGHT;
        }
        TftTouchShield.fillRect(x, y, POINT_WIDTH, POINT_HEIGHT, FELT);
        int color = LIGHT_POINT;
        if (p % 2 == 0) {
            color = DARK_POINT;
        }
        // A triangle from horizontal lines, wide at the board edge and narrowing to the tip.
        for (int k = 0; k < POINT_HEIGHT - 6; k++) {
            int w = (POINT_WIDTH - 2) * (POINT_HEIGHT - 6 - k) / (POINT_HEIGHT - 6);
            int row = TOP + k;
            if (!top) {
                row = BOTTOM - 1 - k;
            }
            if (w > 0) {
                TftTouchShield.drawHorizontalLine(x + 1 + (POINT_WIDTH - 2 - w) / 2, row, w, color);
            }
        }
        int n = board[p];
        int side = HUMAN;
        if (n < 0) {
            side = COMPUTER;
            n = -n;
        }
        for (int i = 0; i < Math.min(n, 5); i++) {
            drawChecker(x + POINT_WIDTH / 2, slotY(p, i), side);
        }
        if (n > 5) {
            drawCount(x + POINT_WIDTH / 2, slotY(p, 4), n, side);
        }
        if (marked) {
            int slot = Math.min(n, 4);
            if (side != HUMAN && n == 1) {
                slot = 0;
            }
            TftTouchShield.drawCircle(x + POINT_WIDTH / 2, slotY(p, slot), CHECKER, MARK);
            TftTouchShield.drawCircle(x + POINT_WIDTH / 2, slotY(p, slot), CHECKER - 1, MARK);
        }
    }

    private static void drawChecker(int cx, int cy, int side) {
        if (side == HUMAN) {
            TftTouchShield.fillCircle(cx, cy, CHECKER, WHITE_CHECKER);
            TftTouchShield.drawCircle(cx, cy, CHECKER, TftTouchShield.GRAY);
            TftTouchShield.drawCircle(cx, cy, CHECKER - 4, 0xC618);
        } else {
            TftTouchShield.fillCircle(cx, cy, CHECKER, BLACK_CHECKER);
            TftTouchShield.drawCircle(cx, cy, CHECKER, TftTouchShield.GRAY);
            TftTouchShield.drawCircle(cx, cy, CHECKER - 4, 0x4208);
        }
    }

    private static void drawCount(int cx, int cy, int n, int side) {
        TftTouchShield.setTextSize(1);
        if (side == HUMAN) {
            TftTouchShield.setTextColor(TftTouchShield.BLACK, WHITE_CHECKER);
        } else {
            TftTouchShield.setTextColor(TftTouchShield.WHITE, BLACK_CHECKER);
        }
        int digits = 1;
        if (n >= 10) {
            digits = 2;
        }
        TftTouchShield.setCursor(cx - digits * 3, cy - 3);
        TftTouchShield.print(n);
    }

    private static void drawBar(byte[] board) {
        TftTouchShield.fillRect(BAR_X, TOP, BAR_WIDTH, BOTTOM - TOP, FRAME);
        int cx = BAR_X + BAR_WIDTH / 2;
        int white = board[HUMAN_BAR];
        int black = board[COMPUTER_BAR];
        if (white > 0) {
            drawChecker(cx, MIDDLE_Y + 30, HUMAN);
            if (white > 1) {
                drawCount(cx, MIDDLE_Y + 30, white, HUMAN);
            }
            if (selected == HUMAN_BAR) {
                TftTouchShield.drawCircle(cx, MIDDLE_Y + 30, CHECKER + 1, MARK);
            }
        }
        if (black > 0) {
            drawChecker(cx, MIDDLE_Y - 30, COMPUTER);
            if (black > 1) {
                drawCount(cx, MIDDLE_Y - 30, black, COMPUTER);
            }
        }
    }

    private static void drawTray(byte[] board, int side) {
        int y = HUMAN_TRAY_Y;
        int color = WHITE_CHECKER;
        int n = board[HUMAN_OFF];
        if (side == COMPUTER) {
            y = COMPUTER_TRAY_Y;
            color = BLACK_CHECKER;
            n = board[COMPUTER_OFF];
        }
        TftTouchShield.fillRect(PANEL_X, y, PANEL_WIDTH, TRAY_HEIGHT, FRAME);
        TftTouchShield.fillRect(PANEL_X + 3, y + 3, PANEL_WIDTH - 6, TRAY_HEIGHT - 6, FELT);
        for (int i = 0; i < n; i++) {
            int sliver = y + TRAY_HEIGHT - 7 - i * 4;
            if (side == COMPUTER) {
                sliver = y + 4 + i * 4;
            }
            TftTouchShield.fillRect(PANEL_X + 6, sliver, 24, 3, color);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, FELT);
        TftTouchShield.setCursor(PANEL_X + 34, y + TRAY_HEIGHT / 2 - 4);
        TftTouchShield.print(n);
    }

    /** The strip between the two rows: dice in play on the right, the message on the left. */
    private static void drawMiddle(byte[] board, int[] dice) {
        TftTouchShield.fillRect(RIGHT_X, MIDDLE_Y - DIE / 2 - 1, BOARD_WIDTH - RIGHT_X, DIE + 2, FRAME);
        for (int i = 0; i < diceCount; i++) {
            drawDie(RIGHT_X + 10 + i * (DIE + 4), dice[i], (usedMask & (1 << i)) == 0);
        }
    }

    private static void drawDie(int x, int value, boolean fresh) {
        int face = TftTouchShield.WHITE;
        if (!fresh) {
            face = TftTouchShield.GRAY;
        }
        int y = MIDDLE_Y - DIE / 2;
        TftTouchShield.fillRect(x, y, DIE, DIE, face);
        TftTouchShield.drawRect(x, y, DIE, DIE, TftTouchShield.BLACK);
        if (value % 2 == 1) {
            pip(x + 10, y + 10);
        }
        if (value >= 2) {
            pip(x + 5, y + 5);
            pip(x + 15, y + 15);
        }
        if (value >= 4) {
            pip(x + 15, y + 5);
            pip(x + 5, y + 15);
        }
        if (value == 6) {
            pip(x + 5, y + 10);
            pip(x + 15, y + 10);
        }
    }

    private static void pip(int x, int y) {
        TftTouchShield.fillCircle(x, y, 2, TftTouchShield.BLACK);
    }

    /** Selects {@code from} (or nothing, for -1) and rings every legal destination. */
    private static void select(byte[] board, int[] dice, int from) {
        int previous = selected;
        selected = from;
        if (previous >= 0 && previous != from) {
            redrawTargets(board, dice, previous, false);
        }
        if (from >= 0) {
            redrawTargets(board, dice, from, true);
        }
    }

    private static void redrawTargets(byte[] board, int[] dice, int from, boolean marked) {
        if (from == HUMAN_BAR) {
            drawBar(board);
        } else {
            drawPoint(board, from, false);
            if (marked) {
                int n = Math.min(count(board, 0, from, HUMAN), 5) - 1;
                TftTouchShield.drawCircle(pointX(from) + POINT_WIDTH / 2, slotY(from, n), CHECKER + 1, MARK);
            }
        }
        boolean trayMarked = false;
        for (int k = 0; k < diceCount; k++) {
            if (!legalHumanMove(board, dice, from, k)) {
                continue;
            }
            int to = target(board, 0, from, dice[k], HUMAN);
            if (to == OFF) {
                trayMarked = marked;
            } else {
                drawPoint(board, to, marked);
            }
        }
        drawTray(board, HUMAN);
        if (trayMarked) {
            TftTouchShield.drawRect(PANEL_X, HUMAN_TRAY_Y, PANEL_WIDTH, TRAY_HEIGHT, MARK);
            TftTouchShield.drawRect(PANEL_X + 1, HUMAN_TRAY_Y + 1, PANEL_WIDTH - 2, TRAY_HEIGHT - 2, MARK);
        }
    }

    private static void drawHeader(byte[] board) {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 5);
        TftTouchShield.print("YOU ");
        TftTouchShield.print(humanScore);
        TftTouchShield.print("  CPU ");
        TftTouchShield.print(computerScore);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, HEADER_BACKGROUND);
        TftTouchShield.print("   PIPS you ");
        TftTouchShield.print(pips(board, 0, HUMAN));
        TftTouchShield.print(" cpu ");
        TftTouchShield.print(pips(board, 0, COMPUTER));
        TftTouchShield.fillRect(NEW_X, 1, WIDTH - NEW_X - 2, HEADER_HEIGHT - 2, BUTTON);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, BUTTON);
        TftTouchShield.setCursor(NEW_X + 12, 5);
        TftTouchShield.print("NEW");
    }

    private static void drawButtons() {
        String label = "ROLL";
        boolean enabled = phase == OPENING || phase == ROLLING || phase == GAME_OVER;
        if (phase == MOVING) {
            label = "DONE";
            enabled = movesLeft == 0;
        } else if (phase == GAME_OVER) {
            label = "PLAY";
        }
        drawButton(BUTTON_Y, label, enabled);
        drawButton(BUTTON_Y + BUTTON_HEIGHT + 8, "UNDO", phase == MOVING);
    }

    private static void drawButton(int y, String label, boolean enabled) {
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        TftTouchShield.fillRect(PANEL_X, y, PANEL_WIDTH, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(PANEL_X, y, PANEL_WIDTH, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(PANEL_X + (PANEL_WIDTH - label.length() * 6) / 2, y + 11);
        TftTouchShield.print(label);
    }

    private static void showMessage(String text) {
        int y = MIDDLE_Y - DIE / 2;
        TftTouchShield.fillRect(4, y, BAR_X - DIE - 12, DIE, FELT);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, FELT);
        TftTouchShield.setCursor(6, y + 6);
        TftTouchShield.print(text);
    }
}
