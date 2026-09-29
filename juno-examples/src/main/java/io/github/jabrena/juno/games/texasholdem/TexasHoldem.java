package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.texasholdem.Table.*;
import static io.github.jabrena.juno.games.texasholdem.Deck.*;
import static io.github.jabrena.juno.games.texasholdem.Controls.*;
import static io.github.jabrena.juno.games.texasholdem.SceneRenderer.*;

/**
 * No-limit Texas Hold'em on the ELEGOO 2.8" TFT touch screen shield: you against three computer
 * players (Ann plays tight, Bob loose and Cal aggressive), everyone starting with
 * {@value #START_CHIPS} chips. The blinds start at 10/20 and double every {@value #HANDS_PER_LEVEL}
 * hands; the game ends when you go broke or win every chip.
 *
 * <p>When it is your turn, {@code FOLD}, {@code CHECK}/{@code CALL}, or set an amount with
 * {@code -}/{@code +} (or {@code ALL}) and {@code BET}/{@code RAISE} to it. Betting follows the usual
 * rules: a raise must be at least as big as the previous one, the dealer button moves round the
 * table, heads-up the dealer posts the small blind, and players who go all in are covered by side
 * pots. After each hand tap {@code DEAL}; between hands {@code NEW} restarts the game.
 *
 * <p>The computer players estimate their chance of winning by dealing out the unknown cards
 * {@value #SIMULATIONS} times at random, then compare it with the pot odds, their style adjusting
 * how much they need to call, how often they raise and how often they bluff. Hands are ranked by
 * one integer: the category (high card ... straight flush) times 2<sup>20</sup> plus up to five
 * deciding ranks in 4-bit fields, so comparing two hands is comparing two ints.
 *
 * <p>Cards are numbered 0-51 as in Blackjack: {@code card % 13} is the rank (0 = ace ...
 * 12 = king) and {@code card / 13} the suit.
 *
 * <p>An animated cover offers HUMAN or CPU control of seat zero; CPU mode lets all four seats play
 * autonomously. Going broke or winning the table returns to the cover. Table flow, player policy,
 * hand evaluation, deck helpers, controls, rendering, card sprites and interludes are separated
 * into package collaborators; this class orchestrates the tournament and owns shared scalar state.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class TexasHoldem {
    static final int COVER_TIMEOUT_MILLIS = 60_000;
    static final int SEATS = 4;
    static final int START_CHIPS = 1000;
    static final int START_SMALL_BLIND = 10;
    static final int HANDS_PER_LEVEL = 8;
    static final int SIMULATIONS = 200;
    static final int MAX_RAISES = 4;

    // Layout of the byte[] cards array.
    static final int DECK = 0;
    static final int HOLE = 52;
    static final int BOARD = 60;
    static final int CARD_BYTES = 65;

    // Layout of the int[] seats array: one block of SEATS ints per field.
    static final int CHIPS = 0;
    static final int BET = 4;
    static final int TOTAL = 8;
    static final int STATE = 12;
    static final int ACTED = 16;
    static final int ACTION = 20;
    static final int SEAT_INTS = 24;

    // Seat states.
    static final int ACTIVE = 0;
    static final int FOLDED = 1;
    static final int ALL_IN = 2;
    static final int OUT = 3;

    // Last actions, as shown under each player.
    static final int NONE = 0;
    static final int FOLD = 1;
    static final int CHECK = 2;
    static final int CALL = 3;
    static final int RAISE = 4;
    static final int SMALL_BLIND = 5;
    static final int BIG_BLIND = 6;
    static final int WINNER = 7;

    // Layout of the int[] work array: hand-evaluation and simulation scratch space.
    static final int COUNTS = 0;
    static final int SUITS = 13;
    static final int HAND = 17;
    static final int REST = 24;
    static final int SCORES = 76;
    static final int CONTRIBUTED = 80;
    static final int WORK_INTS = 84;

    static final int CATEGORY = 1 << 20;

    // Layout (portrait, 240x320).
    static final int WIDTH = 240;
    static final int HEADER_HEIGHT = 18;
    static final int SEAT_Y = 20;
    static final int SEAT_WIDTH = 78;
    static final int SEAT_HEIGHT = 76;
    static final int BOARD_Y = 100;
    static final int POT_Y = 160;
    static final int MESSAGE_Y = 180;
    static final int PLAYER_Y = 196;
    static final int CARD_WIDTH = 40;
    static final int CARD_HEIGHT = 56;
    static final int SMALL_WIDTH = 22;
    static final int SMALL_HEIGHT = 30;
    static final int ROW1_Y = 256;
    static final int ROW2_Y = 288;
    static final int BUTTON_HEIGHT = 28;
    static final int NEW_X = 196;

    static final int FELT = 0x0366;
    static final int SEAT_BACKGROUND = 0x0244;
    static final int CARD_BACK = 0x1152;
    static final int HEADER_BACKGROUND = 0x2945;
    static final int BUTTON = 0xCD05;
    static final int BUTTON_DISABLED = 0x39E7;
    static final int CHIP_TEXT = TftTouchShield.YELLOW;
    static final int HIGHLIGHT = TftTouchShield.YELLOW;

    static int dealer;
    static int smallBlind;
    static int currentBet;
    static int minRaise;
    static int raises;
    static int boardCount;
    static int deckPosition;
    static int handNumber;
    static int raiseTo;
    static int toAct = -1;
    static boolean seeded;

    private TexasHoldem() {
    }

    public static void main(String[] args) {
        byte[] cards = new byte[CARD_BYTES];
        int[] seats = new int[SEAT_INTS];
        int[] work = new int[WORK_INTS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.cover();
                tapped = Controls.waitForTap(COVER_TIMEOUT_MILLIS);
            }
            Controls.choosePlayer();
            playTournament(cards, seats, work);
            Delay.millis(3000);
        }
    }

    private static void playTournament(byte[] cards, int[] seats, int[] work) {
        newGame(seats);
        drawTable(cards, seats, work, false);
        showMessage("Tap DEAL to start", TftTouchShield.WHITE);
        drawDealButtons(true);

        while (seats[CHIPS] > 0 && alive(seats) > 1) {
            boolean newTable = false;
            if (Controls.autopilot) {
                Delay.millis(650);
            } else {
                newTable = waitForDeal();
            }
            if (newTable) {
                newGame(seats);
            }
            if (!seeded) {
                Random.seed(Clock.micros());
                seeded = true;
            }
            playHand(cards, seats, work);
            if (seats[CHIPS] == 0) {
                showMessage("You're out!", TftTouchShield.RED);
                drawDealButtons(false);
            } else if (alive(seats) == 1) {
                showMessage("You won every chip!", TftTouchShield.YELLOW);
                drawDealButtons(false);
            }
            if (seats[CHIPS] > 0 && alive(seats) > 1) {
                drawDealButtons(true);
            }
        }
    }
}
