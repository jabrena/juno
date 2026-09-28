package io.github.jabrena.juno.games;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Rules and computer players of Othello, Mancala and Backgammon. */
class BoardGamesTest {
    private static final int WHITE = 1;
    private static final int BLACK = -1;

    @BeforeEach
    void portrait() {
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT);
    }

    @AfterEach
    void normalTime() {
        Clock.millisPerReading = 1;
    }

    // ---- Othello ----

    /** Leaf counts of the standard Othello perft (passes count as a move). */
    @ParameterizedTest
    @CsvSource({"1, 4", "2, 12", "3, 56", "4, 244", "5, 1396", "6, 8200"})
    void othelloMoveGenerationMatchesPerft(int depth, long leaves) {
        assertThat(perft(othelloStart(), 0, depth, 1, false)).isEqualTo(leaves);
    }

    @Test
    void othelloComputerBeatsAWeightGreedyPlayer() {
        // Each clock reading costs 8 ms, so a two-second move searches about 16k positions.
        Clock.millisPerReading = 8;
        java.util.Random noise = new java.util.Random(42);
        int wins = 0;
        int games = 4;
        int[] moves = new int[32 * 14];
        for (int game = 0; game < games; game++) {
            byte[] board = othelloStart();
            int side = 1;
            int passes = 0;
            while (passes < 2) {
                if (callInt(Othello.class, "countMoves", board, 0, side) == 0) {
                    passes = passes + 1;
                    side = -side;
                    continue;
                }
                passes = 0;
                int square;
                if (side == 1) {
                    square = -1;
                    int best = Integer.MIN_VALUE;
                    for (int candidate = 0; candidate < 64; candidate++) {
                        if (callBoolean(Othello.class, "isLegal", board, 0, candidate, 1)) {
                            int value = callInt(Othello.class, "weight", candidate) + noise.nextInt(30);
                            if (value > best) {
                                best = value;
                                square = candidate;
                            }
                        }
                    }
                } else {
                    square = callInt(Othello.class, "chooseMove", board, moves);
                    assertThat(callBoolean(Othello.class, "isLegal", board, 0, square, -1)).isTrue();
                }
                call(Othello.class, "place", board, 0, square, side);
                side = -side;
            }
            if (callInt(Othello.class, "discDifference", board, 0) < 0) {
                wins = wins + 1;
            }
        }
        assertThat(wins).isGreaterThanOrEqualTo(games - 1);
    }

    private static byte[] othelloStart() {
        byte[] board = new byte[64 * 14];
        board[27] = -1;
        board[36] = -1;
        board[28] = 1;
        board[35] = 1;
        return board;
    }

    private static long perft(byte[] board, int ply, int depth, int side, boolean passed) {
        if (depth == 0) {
            return 1;
        }
        int offset = ply * 64;
        long leaves = 0;
        boolean moved = false;
        for (int square = 0; square < 64; square++) {
            if (callBoolean(Othello.class, "isLegal", board, offset, square, side)) {
                moved = true;
                System.arraycopy(board, offset, board, offset + 64, 64);
                call(Othello.class, "place", board, offset + 64, square, side);
                leaves = leaves + perft(board, ply + 1, depth - 1, -side, false);
            }
        }
        if (!moved) {
            if (passed) {
                return 1;
            }
            System.arraycopy(board, offset, board, offset + 64, 64);
            leaves = perft(board, ply + 1, depth - 1, -side, true);
        }
        return leaves;
    }

    // ---- Mancala ----

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "extra turn in the store | 4,4,4,4,4,4,0,4,4,4,4,4,4,0 | 2 | 1 | 4,4,0,5,5,5,1,4,4,4,4,4,4,0",
        "capture from the opposite pit | 1,0,4,4,4,4,0,4,4,4,4,5,4,0 | 0 | 0 | 0,0,4,4,4,4,6,4,4,4,4,0,4,0",
        "skips the opponent's store | 0,0,0,0,0,1,0,4,0,0,0,0,9,0 | 12 | 0 | 1,1,1,1,0,2,0,5,0,0,0,0,0,3",
        "banks both rows when one empties | 0,0,0,0,0,1,20,1,2,3,0,0,0,21 | 5 | -1 | 0,0,0,0,0,0,21,0,0,0,0,0,0,27"})
    void mancalaSowsCapturesAndEnds(String rule, String before, int pit, int result, String after) {
        byte[] board = new byte[14 * 16];
        byte[] start = bytes(before);
        System.arraycopy(start, 0, board, 0, 14);
        assertThat(callInt(Mancala.class, "sow", board, 0, pit)).isEqualTo(result);
        assertThat(Arrays.copyOf(board, 14)).isEqualTo(bytes(after));
    }

    @Test
    void mancalaComputerBeatsAGreedyPlayer() {
        java.util.Random noise = new java.util.Random(42);
        int wins = 0;
        int games = 10;
        for (int game = 0; game < games; game++) {
            byte[] board = new byte[14 * 16];
            Arrays.fill(board, 0, 14, (byte) 4);
            board[6] = 0;
            board[13] = 0;
            boolean human = game % 2 == 0;
            while (true) {
                int pit;
                if (human) {
                    pit = -1;
                    int best = -1;
                    for (int candidate = 0; candidate < 6; candidate++) {
                        if (board[candidate] > 0) {
                            byte[] trial = Arrays.copyOf(board, board.length);
                            int again = callInt(Mancala.class, "sow", trial, 0, candidate);
                            int gain = trial[6] - board[6] + (again == 1 ? 5 : 0) + noise.nextInt(2);
                            if (gain > best) {
                                best = gain;
                                pit = candidate;
                            }
                        }
                    }
                } else {
                    pit = callInt(Mancala.class, "choosePit", (Object) board);
                }
                int seeds = 0;
                for (int i = 0; i < 14; i++) {
                    seeds = seeds + board[i];
                }
                assertThat(seeds).isEqualTo(48);
                assertThat(board[pit]).as("pit %d is not empty", pit).isPositive();
                int result = callInt(Mancala.class, "sow", board, 0, pit);
                if (result < 0) {
                    break;
                }
                if (result == 0) {
                    human = !human;
                }
            }
            if (board[13] > board[6]) {
                wins = wins + 1;
            }
        }
        assertThat(wins).isGreaterThanOrEqualTo(games - 2);
    }

    private static byte[] bytes(String csv) {
        String[] parts = csv.trim().split(",");
        byte[] values = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Byte.parseByte(parts[i].trim());
        }
        return values;
    }

    // ---- Backgammon ----

    @Test
    void backgammonStartsWith167PipsEach() {
        byte[] board = new byte[28 * 6];
        call(Backgammon.class, "newGame", board, new int[4]);
        assertThat(callInt(Backgammon.class, "pips", board, 0, WHITE)).isEqualTo(167);
        assertThat(callInt(Backgammon.class, "pips", board, 0, BLACK)).isEqualTo(167);
    }

    @Test
    void backgammonEntersFromTheBarFirstAndNotIntoAClosedBoard() {
        byte[] board = new byte[28 * 6];
        int[] dice = {5, 3, 0, 0};
        set(Backgammon.class, "diceCount", 2);
        board[25] = 1;
        board[1] = 14;
        for (int point = 19; point <= 24; point++) {
            board[point] = -2;
        }
        board[27] = 3;
        assertThat(callInt(Backgammon.class, "maxMoves", board, 0, dice, 0, WHITE)).isZero();
        board[22] = 0;
        board[12] = -2;
        assertThat(callInt(Backgammon.class, "maxMoves", board, 0, dice, 0, WHITE)).isEqualTo(2);
        assertThat(callInt(Backgammon.class, "target", board, 0, 25, 3, WHITE)).isEqualTo(22);
        assertThat(callInt(Backgammon.class, "target", board, 0, 1, 5, WHITE)).as("bar first").isEqualTo(-1);
    }

    @Test
    void backgammonHitsABlot() {
        byte[] board = new byte[28 * 6];
        board[10] = 1;
        board[7] = -1;
        call(Backgammon.class, "applyMove", board, 0, 10, 7, WHITE);
        assertThat(board[7]).isEqualTo((byte) 1);
        assertThat(board[0]).as("black's bar").isEqualTo((byte) 1);
    }

    @Test
    void backgammonBearsOffOnlyWhenHomeAndOvershootsOnlyFromTheRear() {
        byte[] board = new byte[28 * 6];
        board[5] = 2;
        board[3] = 1;
        board[26] = 12;
        board[20] = -15;
        assertThat(callInt(Backgammon.class, "target", board, 0, 5, 5, WHITE)).isEqualTo(100);
        assertThat(callInt(Backgammon.class, "target", board, 0, 3, 6, WHITE)).isEqualTo(-1);
        assertThat(callInt(Backgammon.class, "target", board, 0, 5, 6, WHITE)).isEqualTo(100);
        board[8] = 1;
        board[26] = 11;
        assertThat(callInt(Backgammon.class, "target", board, 0, 5, 5, WHITE)).isEqualTo(-1);
    }

    @Test
    void backgammonMustPlayTheHigherDieWhenOnlyOneFits() {
        byte[] board = new byte[28 * 6];
        board[13] = 1;
        board[26] = 14;
        board[2] = -2;
        board[20] = -13;
        int[] dice = {6, 5, 0, 0};
        set(Backgammon.class, "diceCount", 2);
        set(Backgammon.class, "usedMask", 0);
        int moves = callInt(Backgammon.class, "maxMoves", board, 0, dice, 0, WHITE);
        set(Backgammon.class, "movesLeft", moves);
        assertThat(moves).isEqualTo(1);
        assertThat(callBoolean(Backgammon.class, "legalHumanMove", board, dice, 13, 0)).isTrue();
        assertThat(callBoolean(Backgammon.class, "legalHumanMove", board, dice, 13, 1)).isFalse();
    }

    /** Full games against random legal play: nothing is lost, every game ends, the computer wins. */
    @Test
    void backgammonComputerBeatsRandomPlayWithoutLosingAChecker() {
        java.util.Random chooser = new java.util.Random(7);
        int[] dice = new int[4];
        int[] path = new int[8];
        int[] best = new int[8];
        int wins = 0;
        int games = 20;
        for (int game = 0; game < games; game++) {
            Random.seed(game);
            byte[] board = new byte[28 * 6];
            call(Backgammon.class, "newGame", board, dice);
            int side = game % 2 == 0 ? WHITE : BLACK;
            int turns = 0;
            while (board[26] < 15 && board[27] < 15) {
                turns = turns + 1;
                assertThat(turns).as("game %d ends", game).isLessThan(2000);
                call(Backgammon.class, "roll", (Object) dice);
                if (side == WHITE) {
                    playRandomly(board, dice, chooser);
                } else {
                    playComputer(board, dice, path, best);
                }
                assertThat(checkers(board, WHITE)).isEqualTo(15);
                assertThat(checkers(board, BLACK)).isEqualTo(15);
                side = -side;
            }
            if (board[27] == 15) {
                wins = wins + 1;
            }
        }
        assertThat(wins).isGreaterThanOrEqualTo(games - 2);
    }

    private static void playRandomly(byte[] board, int[] dice, java.util.Random chooser) {
        set(Backgammon.class, "usedMask", 0);
        int left = callInt(Backgammon.class, "maxMoves", board, 0, dice, 0, WHITE);
        set(Backgammon.class, "movesLeft", left);
        while (left > 0) {
            List<int[]> legal = new ArrayList<>();
            int count = getInt(Backgammon.class, "diceCount");
            for (int from = 1; from <= 25; from++) {
                for (int die = 0; die < count; die++) {
                    if (callBoolean(Backgammon.class, "legalHumanMove", board, dice, from, die)) {
                        legal.add(new int[] {from, die});
                    }
                }
            }
            assertThat(legal).as("a legal move exists while dice remain usable").isNotEmpty();
            int[] move = legal.get(chooser.nextInt(legal.size()));
            int to = callInt(Backgammon.class, "target", board, 0, move[0], dice[move[1]], WHITE);
            call(Backgammon.class, "applyMove", board, 0, move[0], to, WHITE);
            int used = getInt(Backgammon.class, "usedMask") | (1 << move[1]);
            set(Backgammon.class, "usedMask", used);
            left = callInt(Backgammon.class, "maxMoves", board, 0, dice, used, WHITE);
            set(Backgammon.class, "movesLeft", left);
        }
    }

    private static void playComputer(byte[] board, int[] dice, int[] path, int[] best) {
        int length = callInt(Backgammon.class, "maxMoves", board, 0, dice, 0, BLACK);
        if (length == 0) {
            return;
        }
        set(Backgammon.class, "bestScore", -1000000);
        set(Backgammon.class, "bestLength", length);
        boolean higherOnly = length == 1 && getInt(Backgammon.class, "diceCount") == 2
                && callBoolean(Backgammon.class, "canUseDie", board, dice, 0, BLACK);
        call(Backgammon.class, "findBest", board, 0, dice, 0, -1, BLACK, path, best, higherOnly);
        for (int i = 0; i < length; i++) {
            call(Backgammon.class, "applyMove", board, 0, best[2 * i], best[2 * i + 1], BLACK);
        }
    }

    private static int checkers(byte[] board, int side) {
        int count = side == WHITE ? board[25] + board[26] : board[0] + board[27];
        for (int point = 1; point <= 24; point++) {
            count = count + Math.max(0, board[point] * side);
        }
        return count;
    }
}
