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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Rules and computer players of Backgammon. */
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
