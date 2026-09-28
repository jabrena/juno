package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

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
 * <p>Cards are numbered 0-51 as in {@link Blackjack}: {@code card % 13} is the rank (0 = ace ...
 * 12 = king) and {@code card / 13} the suit.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class TexasHoldem {
    private static final int SEATS = 4;
    private static final int START_CHIPS = 1000;
    private static final int START_SMALL_BLIND = 10;
    private static final int HANDS_PER_LEVEL = 8;
    private static final int SIMULATIONS = 200;
    private static final int MAX_RAISES = 4;

    // Layout of the byte[] cards array.
    private static final int DECK = 0;
    private static final int HOLE = 52;
    private static final int BOARD = 60;
    private static final int CARD_BYTES = 65;

    // Layout of the int[] seats array: one block of SEATS ints per field.
    private static final int CHIPS = 0;
    private static final int BET = 4;
    private static final int TOTAL = 8;
    private static final int STATE = 12;
    private static final int ACTED = 16;
    private static final int ACTION = 20;
    private static final int SEAT_INTS = 24;

    // Seat states.
    private static final int ACTIVE = 0;
    private static final int FOLDED = 1;
    private static final int ALL_IN = 2;
    private static final int OUT = 3;

    // Last actions, as shown under each player.
    private static final int NONE = 0;
    private static final int FOLD = 1;
    private static final int CHECK = 2;
    private static final int CALL = 3;
    private static final int RAISE = 4;
    private static final int SMALL_BLIND = 5;
    private static final int BIG_BLIND = 6;
    private static final int WINNER = 7;

    // Layout of the int[] work array: hand-evaluation and simulation scratch space.
    private static final int COUNTS = 0;
    private static final int SUITS = 13;
    private static final int HAND = 17;
    private static final int REST = 24;
    private static final int SCORES = 76;
    private static final int CONTRIBUTED = 80;
    private static final int WORK_INTS = 84;

    private static final int CATEGORY = 1 << 20;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEADER_HEIGHT = 18;
    private static final int SEAT_Y = 20;
    private static final int SEAT_WIDTH = 78;
    private static final int SEAT_HEIGHT = 76;
    private static final int BOARD_Y = 100;
    private static final int POT_Y = 160;
    private static final int MESSAGE_Y = 180;
    private static final int PLAYER_Y = 196;
    private static final int CARD_WIDTH = 40;
    private static final int CARD_HEIGHT = 56;
    private static final int SMALL_WIDTH = 22;
    private static final int SMALL_HEIGHT = 30;
    private static final int ROW1_Y = 256;
    private static final int ROW2_Y = 288;
    private static final int BUTTON_HEIGHT = 28;
    private static final int NEW_X = 196;

    private static final int FELT = 0x0366;
    private static final int SEAT_BACKGROUND = 0x0244;
    private static final int CARD_BACK = 0x1152;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON = 0xCD05;
    private static final int BUTTON_DISABLED = 0x39E7;
    private static final int CHIP_TEXT = TftTouchShield.YELLOW;
    private static final int HIGHLIGHT = TftTouchShield.YELLOW;

    private static int dealer;
    private static int smallBlind;
    private static int currentBet;
    private static int minRaise;
    private static int raises;
    private static int boardCount;
    private static int deckPosition;
    private static int handNumber;
    private static int raiseTo;
    private static int toAct = -1;
    private static boolean seeded;

    private TexasHoldem() {
    }

    public static void main(String[] args) {
        byte[] cards = new byte[CARD_BYTES];
        int[] seats = new int[SEAT_INTS];
        int[] work = new int[WORK_INTS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        newGame(seats);
        drawTable(cards, seats, work, false);
        showMessage("Tap DEAL to start", TftTouchShield.WHITE);
        drawDealButtons(true);

        while (true) {
            if (waitForDeal()) {
                newGame(seats);
            }
            if (!seeded) {
                Random.seed(Clock.micros());
                seeded = true;
            }
            playHand(cards, seats, work);
            if (seats[CHIPS] == 0) {
                showMessage("You're out! NEW to retry", TftTouchShield.RED);
                drawDealButtons(false);
                waitForNew();
                newGame(seats);
                drawTable(cards, seats, work, false);
                showMessage("Tap DEAL to start", TftTouchShield.WHITE);
            } else if (alive(seats) == 1) {
                showMessage("You won every chip!", TftTouchShield.YELLOW);
                drawDealButtons(false);
                waitForNew();
                newGame(seats);
                drawTable(cards, seats, work, false);
                showMessage("Tap DEAL to start", TftTouchShield.WHITE);
            }
            drawDealButtons(true);
        }
    }

    // ---- Game flow ----

    private static void newGame(int[] seats) {
        for (int seat = 0; seat < SEATS; seat++) {
            seats[CHIPS + seat] = START_CHIPS;
            seats[BET + seat] = 0;
            seats[TOTAL + seat] = 0;
            seats[STATE + seat] = FOLDED;
            seats[ACTION + seat] = NONE;
        }
        handNumber = 0;
        dealer = Random.nextInt(SEATS);
        boardCount = 0;
        smallBlind = START_SMALL_BLIND;
    }

    private static void playHand(byte[] cards, int[] seats, int[] work) {
        handNumber = handNumber + 1;
        smallBlind = START_SMALL_BLIND << Math.min(6, (handNumber - 1) / HANDS_PER_LEVEL);
        for (int seat = 0; seat < SEATS; seat++) {
            seats[BET + seat] = 0;
            seats[TOTAL + seat] = 0;
            seats[ACTED + seat] = 0;
            seats[ACTION + seat] = NONE;
            if (seats[CHIPS + seat] == 0) {
                seats[STATE + seat] = OUT;
            } else {
                seats[STATE + seat] = ACTIVE;
            }
        }
        dealer = nextSeat(seats, dealer, true);
        int small = nextSeat(seats, dealer, true);
        if (alive(seats) == 2) {
            small = dealer;
        }
        int big = nextSeat(seats, small, true);
        shuffle(cards);
        for (int round = 0; round < 2; round++) {
            for (int seat = 0; seat < SEATS; seat++) {
                if (seats[STATE + seat] != OUT) {
                    cards[HOLE + seat * 2 + round] = cards[DECK + deckPosition];
                    deckPosition = deckPosition + 1;
                }
            }
        }
        boardCount = 0;
        currentBet = 0;
        pay(seats, small, smallBlind);
        seats[ACTION + small] = SMALL_BLIND;
        pay(seats, big, smallBlind * 2);
        seats[ACTION + big] = BIG_BLIND;
        currentBet = Math.max(seats[BET + small], seats[BET + big]);
        minRaise = smallBlind * 2;
        raises = 0;
        drawTable(cards, seats, work, false);
        showMessage("", TftTouchShield.WHITE);

        if (!bettingRound(cards, seats, work, nextSeat(seats, big, true))) {
            return;
        }
        for (int street = 0; street < 3; street++) {
            for (int seat = 0; seat < SEATS; seat++) {
                seats[BET + seat] = 0;
                seats[ACTED + seat] = 0;
                if (seats[STATE + seat] == ACTIVE) {
                    seats[ACTION + seat] = NONE;
                }
            }
            currentBet = 0;
            minRaise = smallBlind * 2;
            raises = 0;
            int deal = 1;
            if (street == 0) {
                deal = 3;
            }
            for (int i = 0; i < deal; i++) {
                cards[BOARD + boardCount] = cards[DECK + deckPosition];
                deckPosition = deckPosition + 1;
                boardCount = boardCount + 1;
            }
            drawTable(cards, seats, work, false);
            Delay.millis(500);
            if (countState(seats, ACTIVE) >= 2 && !bettingRound(cards, seats, work, nextSeat(seats, dealer, true))) {
                return;
            }
        }
        showdown(cards, seats, work);
    }

    /**
     * Runs a betting round starting at {@code first}. Returns false when everyone but one player
     * folded (that player has then been paid), true when the hand goes on.
     */
    private static boolean bettingRound(byte[] cards, int[] seats, int[] work, int first) {
        int seat = first;
        while (true) {
            if (SEATS - countState(seats, FOLDED) - countState(seats, OUT) == 1) {
                winByFold(cards, seats, work);
                return false;
            }
            if (roundComplete(seats)) {
                return true;
            }
            if (seats[STATE + seat] == ACTIVE
                    && (seats[ACTED + seat] == 0 || seats[BET + seat] < currentBet)) {
                toAct = seat;
                drawSeat(cards, seats, work, seat, false);
                if (seat == 0) {
                    humanTurn(cards, seats, work);
                } else {
                    computerTurn(cards, seats, work, seat);
                }
                toAct = -1;
                drawSeat(cards, seats, work, seat, false);
                drawPot(seats);
            }
            seat = (seat + 1) % SEATS;
        }
    }

    /** Whether every player who can still act has acted and matched the current bet. */
    private static boolean roundComplete(int[] seats) {
        for (int seat = 0; seat < SEATS; seat++) {
            if (seats[STATE + seat] == ACTIVE
                    && (seats[ACTED + seat] == 0 || seats[BET + seat] < currentBet)) {
                // A lone active player facing no bet has nobody left to bet against.
                if (countState(seats, ACTIVE) == 1 && seats[BET + seat] >= currentBet) {
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    /** Moves up to {@code amount} of {@code seat}'s chips into its bet. */
    private static void pay(int[] seats, int seat, int amount) {
        int paid = Math.min(amount, seats[CHIPS + seat]);
        seats[CHIPS + seat] = seats[CHIPS + seat] - paid;
        seats[BET + seat] = seats[BET + seat] + paid;
        seats[TOTAL + seat] = seats[TOTAL + seat] + paid;
        if (seats[CHIPS + seat] == 0) {
            seats[STATE + seat] = ALL_IN;
        }
    }

    private static void fold(int[] seats, int seat) {
        seats[STATE + seat] = FOLDED;
        seats[ACTION + seat] = FOLD;
    }

    private static void checkOrCall(int[] seats, int seat) {
        if (seats[BET + seat] >= currentBet) {
            seats[ACTION + seat] = CHECK;
        } else {
            pay(seats, seat, currentBet - seats[BET + seat]);
            seats[ACTION + seat] = CALL;
        }
        seats[ACTED + seat] = 1;
    }

    /** Bets or raises to a total of {@code target} this round (clamped to the player's stack). */
    private static void raise(int[] seats, int seat, int target) {
        target = Math.min(target, seats[BET + seat] + seats[CHIPS + seat]);
        int increase = target - currentBet;
        pay(seats, seat, target - seats[BET + seat]);
        if (increase >= minRaise) {
            // A full raise reopens the betting for everyone else.
            minRaise = increase;
            for (int other = 0; other < SEATS; other++) {
                seats[ACTED + other] = 0;
            }
        }
        currentBet = Math.max(currentBet, target);
        raises = raises + 1;
        seats[ACTION + seat] = RAISE;
        seats[ACTED + seat] = 1;
    }

    private static void winByFold(byte[] cards, int[] seats, int[] work) {
        int winner = 0;
        int pot = 0;
        for (int seat = 0; seat < SEATS; seat++) {
            pot = pot + seats[TOTAL + seat];
            seats[TOTAL + seat] = 0;
            seats[BET + seat] = 0;
            if (seats[STATE + seat] == ACTIVE || seats[STATE + seat] == ALL_IN) {
                winner = seat;
            }
        }
        seats[CHIPS + winner] = seats[CHIPS + winner] + pot;
        seats[ACTION + winner] = WINNER;
        drawTable(cards, seats, work, false);
        showWinner(winner, pot, -1);
    }

    /**
     * Reveals the hands and pays every pot and side pot: contributions are peeled off in levels
     * (the smallest remaining contribution first), and each level's chips go to the best hand still
     * in among the players who put that much in.
     */
    private static void showdown(byte[] cards, int[] seats, int[] work) {
        for (int seat = 0; seat < SEATS; seat++) {
            seats[BET + seat] = 0;
            work[CONTRIBUTED + seat] = seats[TOTAL + seat];
            seats[TOTAL + seat] = 0;
            if (seats[STATE + seat] == ACTIVE || seats[STATE + seat] == ALL_IN) {
                work[SCORES + seat] = scoreSeat(cards, work, seat, 5);
            } else {
                work[SCORES + seat] = -1;
            }
        }
        drawTable(cards, seats, work, true);
        int mainWinner = -1;
        int mainPot = 0;
        int mainScore = -1;
        while (true) {
            int level = 0;
            for (int seat = 0; seat < SEATS; seat++) {
                int left = work[CONTRIBUTED + seat];
                if (left > 0 && (level == 0 || left < level)) {
                    level = left;
                }
            }
            if (level == 0) {
                break;
            }
            int slice = 0;
            int eligible = 0;
            int best = -1;
            for (int seat = 0; seat < SEATS; seat++) {
                if (work[CONTRIBUTED + seat] > 0) {
                    slice = slice + level;
                    work[CONTRIBUTED + seat] = work[CONTRIBUTED + seat] - level;
                    eligible = eligible | (1 << seat);
                    best = Math.max(best, work[SCORES + seat]);
                }
            }
            int winners = 0;
            for (int seat = 0; seat < SEATS; seat++) {
                if ((eligible & (1 << seat)) != 0 && work[SCORES + seat] == best) {
                    winners = winners + 1;
                }
            }
            // The odd chip goes to the first winner after the dealer.
            int share = slice / winners;
            int odd = slice - share * winners;
            for (int i = 1; i <= SEATS; i++) {
                int seat = (dealer + i) % SEATS;
                if ((eligible & (1 << seat)) != 0 && work[SCORES + seat] == best) {
                    seats[CHIPS + seat] = seats[CHIPS + seat] + share + odd;
                    odd = 0;
                    if (best >= 0) {
                        seats[ACTION + seat] = WINNER;
                    }
                    if (best > mainScore) {
                        mainScore = best;
                        mainWinner = seat;
                        mainPot = 0;
                    }
                    if (seat == mainWinner) {
                        mainPot = mainPot + share;
                    }
                }
            }
        }
        drawTable(cards, seats, work, true);
        showWinner(mainWinner, mainPot, mainScore);
    }

    // ---- Players ----

    private static void humanTurn(byte[] cards, int[] seats, int[] work) {
        int toCall = currentBet - seats[BET];
        int stack = seats[BET] + seats[CHIPS];
        int minimum = currentBet + minRaise;
        if (currentBet == 0) {
            minimum = smallBlind * 2;
        }
        boolean canRaise = seats[CHIPS] > toCall && raises < MAX_RAISES;
        raiseTo = Math.min(minimum, stack);
        if (toCall > 0) {
            showMessage("Your turn", TftTouchShield.YELLOW);
        } else {
            showMessage("Your turn: check or bet", TftTouchShield.YELLOW);
        }
        drawActionButtons(seats, toCall, canRaise);
        while (true) {
            int button = waitForButton();
            if (button == 0 && toCall > 0) {
                fold(seats, 0);
                break;
            }
            if (button == 1) {
                checkOrCall(seats, 0);
                break;
            }
            if (button == 2 && canRaise) {
                raise(seats, 0, raiseTo);
                break;
            }
            if (canRaise && button >= 3) {
                if (button == 3) {
                    raiseTo = Math.max(Math.min(minimum, stack), raiseTo - smallBlind * 2);
                } else if (button == 4) {
                    raiseTo = Math.min(stack, raiseTo + smallBlind * 2);
                } else {
                    raiseTo = stack;
                }
                drawActionButtons(seats, toCall, canRaise);
            }
        }
        showMessage("", TftTouchShield.WHITE);
        clearControls();
    }

    /** Decides from the simulated chance of winning, the pot odds and the seat's playing style. */
    private static void computerTurn(byte[] cards, int[] seats, int[] work, int seat) {
        Delay.millis(350);
        int opponents = SEATS - countState(seats, FOLDED) - countState(seats, OUT) - 1;
        float equity = equity(cards, work, seat, opponents);
        int toCall = currentBet - seats[BET + seat];
        int pot = 0;
        for (int other = 0; other < SEATS; other++) {
            pot = pot + seats[TOTAL + other];
        }
        float potOdds = 0;
        if (toCall > 0) {
            potOdds = (float) toCall / (pot + toCall);
        }
        // Styles: Ann (1) tight, Bob (2) loose, Cal (3) aggressive.
        float callMargin = 0.08f;
        float raiseMargin = 0.22f;
        int bluffPercent = 3;
        if (seat == 2) {
            callMargin = -0.04f;
            raiseMargin = 0.25f;
            bluffPercent = 6;
        } else if (seat == 3) {
            callMargin = 0.02f;
            raiseMargin = 0.12f;
            bluffPercent = 12;
        }
        float fair = 1.0f / (opponents + 1);
        boolean canRaise = seats[CHIPS + seat] > toCall && raises < MAX_RAISES;
        boolean bluff = Random.nextInt(100) < bluffPercent && boardCount < 5;
        if (canRaise && (equity > fair + raiseMargin || bluff)) {
            // Size between half the pot and the whole pot, more with stronger hands.
            int size = pot / 2 + (int) (pot * Math.min(0.5f, equity - fair));
            int target = currentBet + Math.max(minRaise, size);
            if (currentBet == 0) {
                target = Math.max(smallBlind * 2, size);
            }
            // With most of the stack committed anyway, just go all in.
            if (target * 3 > (seats[BET + seat] + seats[CHIPS + seat]) * 2) {
                target = seats[BET + seat] + seats[CHIPS + seat];
            }
            raise(seats, seat, target);
        } else if (toCall == 0 || equity >= potOdds + callMargin) {
            checkOrCall(seats, seat);
        } else {
            fold(seats, seat);
        }
        Delay.millis(450);
    }

    /**
     * Estimated chance that {@code seat}'s hand wins against {@code opponents} random hands,
     * dealing the unknown cards at random {@value #SIMULATIONS} times (ties count half).
     */
    private static float equity(byte[] cards, int[] work, int seat, int opponents) {
        // The deck left once our own hole cards and the board are removed.
        int remaining = 0;
        for (int card = 0; card < 52; card++) {
            boolean known = card == cards[HOLE + seat * 2] || card == cards[HOLE + seat * 2 + 1];
            for (int i = 0; i < boardCount; i++) {
                if (card == cards[BOARD + i]) {
                    known = true;
                }
            }
            if (!known) {
                work[REST + remaining] = card;
                remaining = remaining + 1;
            }
        }
        int points = 0;
        int needed = 5 - boardCount + 2 * opponents;
        for (int simulation = 0; simulation < SIMULATIONS; simulation++) {
            // Partial Fisher-Yates: the first `needed` cards of REST become this deal's cards.
            for (int i = 0; i < needed; i++) {
                int j = i + Random.nextInt(remaining - i);
                int swap = work[REST + i];
                work[REST + i] = work[REST + j];
                work[REST + j] = swap;
            }
            for (int i = 0; i < boardCount; i++) {
                work[HAND + 2 + i] = cards[BOARD + i];
            }
            for (int i = boardCount; i < 5; i++) {
                work[HAND + 2 + i] = work[REST + i - boardCount];
            }
            work[HAND] = cards[HOLE + seat * 2];
            work[HAND + 1] = cards[HOLE + seat * 2 + 1];
            int mine = evaluate(work, 7);
            int best = 0;
            for (int opponent = 0; opponent < opponents; opponent++) {
                int first = 5 - boardCount + 2 * opponent;
                work[HAND] = work[REST + first];
                work[HAND + 1] = work[REST + first + 1];
                best = Math.max(best, evaluate(work, 7));
            }
            if (mine > best) {
                points = points + 2;
            } else if (mine == best) {
                points = points + 1;
            }
        }
        return points / (2.0f * SIMULATIONS);
    }

    // ---- Hand evaluation ----

    /** Score of {@code seat}'s hole cards with the first {@code shown} board cards. */
    private static int scoreSeat(byte[] cards, int[] work, int seat, int shown) {
        work[HAND] = cards[HOLE + seat * 2];
        work[HAND + 1] = cards[HOLE + seat * 2 + 1];
        for (int i = 0; i < shown; i++) {
            work[HAND + 2 + i] = cards[BOARD + i];
        }
        return evaluate(work, 2 + shown);
    }

    /**
     * Ranks the best five-card hand among the {@code n} (5-7) cards at {@code work[HAND]}: the
     * category times {@link #CATEGORY} plus the deciding ranks (2 = 0 ... ace = 12) in 4-bit fields.
     */
    private static int evaluate(int[] work, int n) {
        for (int i = 0; i < 13; i++) {
            work[COUNTS + i] = 0;
        }
        for (int i = 0; i < 4; i++) {
            work[SUITS + i] = 0;
        }
        int ranks = 0;
        for (int i = 0; i < n; i++) {
            int card = work[HAND + i];
            int rank = value(card);
            work[COUNTS + rank] = work[COUNTS + rank] + 1;
            work[SUITS + card / 13] = work[SUITS + card / 13] + 1;
            ranks = ranks | (1 << rank);
        }
        int flushSuit = -1;
        for (int suit = 0; suit < 4; suit++) {
            if (work[SUITS + suit] >= 5) {
                flushSuit = suit;
            }
        }
        int flushRanks = 0;
        if (flushSuit >= 0) {
            for (int i = 0; i < n; i++) {
                if (work[HAND + i] / 13 == flushSuit) {
                    flushRanks = flushRanks | (1 << value(work[HAND + i]));
                }
            }
            int straightFlush = straightTop(flushRanks);
            if (straightFlush >= 0) {
                return 8 * CATEGORY + (straightFlush << 16);
            }
        }
        int quad = -1;
        int trips = -1;
        int secondTrips = -1;
        int pair = -1;
        int secondPair = -1;
        for (int rank = 12; rank >= 0; rank--) {
            int count = work[COUNTS + rank];
            if (count == 4) {
                quad = rank;
            } else if (count == 3) {
                if (trips < 0) {
                    trips = rank;
                } else if (secondTrips < 0) {
                    secondTrips = rank;
                }
            } else if (count == 2) {
                if (pair < 0) {
                    pair = rank;
                } else if (secondPair < 0) {
                    secondPair = rank;
                }
            }
        }
        if (quad >= 0) {
            return 7 * CATEGORY + (quad << 16) + (topRanks(ranks & ~(1 << quad), 1) << 12);
        }
        if (trips >= 0 && (secondTrips >= 0 || pair >= 0)) {
            return 6 * CATEGORY + (trips << 16) + (Math.max(secondTrips, pair) << 12);
        }
        if (flushSuit >= 0) {
            return 5 * CATEGORY + topRanks(flushRanks, 5);
        }
        int straight = straightTop(ranks);
        if (straight >= 0) {
            return 4 * CATEGORY + (straight << 16);
        }
        if (trips >= 0) {
            return 3 * CATEGORY + (trips << 16) + (topRanks(ranks & ~(1 << trips), 2) << 8);
        }
        if (pair >= 0 && secondPair >= 0) {
            int kickers = ranks & ~(1 << pair) & ~(1 << secondPair);
            return 2 * CATEGORY + (pair << 16) + (secondPair << 12) + (topRanks(kickers, 1) << 8);
        }
        if (pair >= 0) {
            return CATEGORY + (pair << 16) + (topRanks(ranks & ~(1 << pair), 3) << 4);
        }
        return topRanks(ranks, 5);
    }

    /** Rank value with aces high: 2 = 0 ... king = 11, ace = 12. */
    private static int value(int card) {
        int rank = card % 13;
        if (rank == 0) {
            return 12;
        }
        return rank - 1;
    }

    /** The highest {@code count} ranks set in {@code mask}, packed high to low in 4-bit fields. */
    private static int topRanks(int mask, int count) {
        int packed = 0;
        int taken = 0;
        for (int rank = 12; rank >= 0 && taken < count; rank--) {
            if ((mask & (1 << rank)) != 0) {
                packed = (packed << 4) | rank;
                taken = taken + 1;
            }
        }
        // Left-align so that fewer, higher kickers still compare correctly.
        while (taken < count) {
            packed = packed << 4;
            taken = taken + 1;
        }
        return packed;
    }

    /** Top rank of the highest five-in-a-row in {@code mask} (the wheel A-2-3-4-5 tops at 5), or -1. */
    private static int straightTop(int mask) {
        for (int top = 12; top >= 4; top--) {
            if (((mask >> (top - 4)) & 31) == 31) {
                return top;
            }
        }
        if ((mask & 0x100F) == 0x100F) {
            return 3;
        }
        return -1;
    }

    private static String categoryName(int score) {
        int category = score / CATEGORY;
        if (category == 8) {
            if (score >> 16 == 8 * 16 + 12) {
                return "Royal flush";
            }
            return "Straight flush";
        }
        if (category == 7) {
            return "Four of a kind";
        }
        if (category == 6) {
            return "Full house";
        }
        if (category == 5) {
            return "Flush";
        }
        if (category == 4) {
            return "Straight";
        }
        if (category == 3) {
            return "Three of a kind";
        }
        if (category == 2) {
            return "Two pair";
        }
        if (category == 1) {
            return "Pair";
        }
        return "High card";
    }

    // ---- Table helpers ----

    private static void shuffle(byte[] cards) {
        for (int i = 0; i < 52; i++) {
            cards[DECK + i] = (byte) i;
        }
        for (int i = 51; i > 0; i--) {
            int j = Random.nextInt(i + 1);
            byte swap = cards[DECK + i];
            cards[DECK + i] = cards[DECK + j];
            cards[DECK + j] = swap;
        }
        deckPosition = 0;
    }

    /** The next seat after {@code seat} still in the game (with chips), or -1. */
    private static int nextSeat(int[] seats, int seat, boolean skipOut) {
        for (int i = 1; i <= SEATS; i++) {
            int next = (seat + i) % SEATS;
            if (!skipOut || seats[STATE + next] != OUT) {
                return next;
            }
        }
        return -1;
    }

    private static int alive(int[] seats) {
        int count = 0;
        for (int seat = 0; seat < SEATS; seat++) {
            if (seats[CHIPS + seat] > 0 || seats[TOTAL + seat] > 0) {
                count = count + 1;
            }
        }
        return count;
    }

    private static int countState(int[] seats, int state) {
        int count = 0;
        for (int seat = 0; seat < SEATS; seat++) {
            if (seats[STATE + seat] == state) {
                count = count + 1;
            }
        }
        return count;
    }

    // ---- Input ----

    /**
     * Waits for one of the controls: 0 FOLD, 1 CHECK/CALL, 2 BET/RAISE, 3 minus, 4 plus, 5 ALL,
     * or 6 for the header's NEW button.
     */
    private static int waitForButton() {
        while (true) {
            if (TftTouchShield.readTouch()) {
                int x = TftTouchShield.touchX();
                int y = TftTouchShield.touchY();
                waitForRelease();
                if (y < HEADER_HEIGHT && x >= NEW_X) {
                    return 6;
                }
                if (y >= ROW2_Y) {
                    return Math.min(2, x * 3 / WIDTH);
                }
                if (y >= ROW1_Y && y < ROW1_Y + BUTTON_HEIGHT) {
                    if (x < 44) {
                        return 3;
                    }
                    if (x >= 132 && x < 172) {
                        return 4;
                    }
                    if (x >= 176) {
                        return 5;
                    }
                }
            }
            Delay.millis(10);
        }
    }

    /** Waits for DEAL; returns true when NEW was tapped instead. */
    private static boolean waitForDeal() {
        while (true) {
            int button = waitForButton();
            if (button == 6) {
                return true;
            }
            if (button <= 2) {
                return false;
            }
        }
    }

    private static void waitForNew() {
        while (waitForButton() != 6) {
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

    private static void drawTable(byte[] cards, int[] seats, int[] work, boolean reveal) {
        TftTouchShield.fillRect(0, HEADER_HEIGHT, WIDTH, ROW1_Y - HEADER_HEIGHT - 2, FELT);
        drawHeader();
        for (int seat = 0; seat < SEATS; seat++) {
            drawSeat(cards, seats, work, seat, reveal);
        }
        for (int i = 0; i < 5; i++) {
            int x = 8 + i * 46;
            if (i < boardCount) {
                drawCard(x, BOARD_Y, cards[BOARD + i]);
            } else {
                TftTouchShield.drawRect(x, BOARD_Y, CARD_WIDTH, CARD_HEIGHT, SEAT_BACKGROUND);
            }
        }
        drawPot(seats);
    }

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 5);
        TftTouchShield.print("HAND ");
        TftTouchShield.print(handNumber);
        TftTouchShield.print("   BLINDS ");
        TftTouchShield.print(smallBlind);
        TftTouchShield.print("/");
        TftTouchShield.print(smallBlind * 2);
        TftTouchShield.fillRect(NEW_X, 1, WIDTH - NEW_X - 2, HEADER_HEIGHT - 2, BUTTON);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, BUTTON);
        TftTouchShield.setCursor(NEW_X + 12, 5);
        TftTouchShield.print("NEW");
    }

    private static void drawPot(int[] seats) {
        int pot = 0;
        for (int seat = 0; seat < SEATS; seat++) {
            pot = pot + seats[TOTAL + seat];
        }
        TftTouchShield.fillRect(0, POT_Y, WIDTH, 18, FELT);
        if (pot == 0) {
            return;
        }
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(CHIP_TEXT, FELT);
        int digits = String.valueOf(pot).length();
        TftTouchShield.setCursor((WIDTH - (4 + digits) * 12) / 2, POT_Y + 1);
        TftTouchShield.print("Pot ");
        TftTouchShield.print(pot);
    }

    /** A computer seat's box along the top, or your own area above the controls (seat 0). */
    private static void drawSeat(byte[] cards, int[] seats, int[] work, int seat, boolean reveal) {
        int state = seats[STATE + seat];
        if (seat == 0) {
            drawPlayer(cards, seats, work, reveal);
            return;
        }
        int x = 2 + (seat - 1) * (SEAT_WIDTH + 1);
        int border = SEAT_BACKGROUND;
        if (toAct == seat) {
            border = HIGHLIGHT;
        } else if (seats[ACTION + seat] == WINNER) {
            border = TftTouchShield.GREEN;
        }
        TftTouchShield.fillRect(x, SEAT_Y, SEAT_WIDTH, SEAT_HEIGHT, SEAT_BACKGROUND);
        TftTouchShield.drawRect(x, SEAT_Y, SEAT_WIDTH, SEAT_HEIGHT, border);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SEAT_BACKGROUND);
        TftTouchShield.setCursor(x + 4, SEAT_Y + 4);
        TftTouchShield.print(seatName(seat));
        if (seat == dealer && state != OUT && handNumber > 0) {
            drawDealerButton(x + SEAT_WIDTH - 10, SEAT_Y + 7);
        }
        TftTouchShield.setTextColor(CHIP_TEXT, SEAT_BACKGROUND);
        TftTouchShield.setCursor(x + 4, SEAT_Y + 14);
        TftTouchShield.print(seats[CHIPS + seat]);
        if (state == OUT) {
            TftTouchShield.setTextColor(TftTouchShield.GRAY, SEAT_BACKGROUND);
            TftTouchShield.setCursor(x + 4, SEAT_Y + 36);
            TftTouchShield.print("OUT");
            return;
        }
        if (state != FOLDED) {
            for (int i = 0; i < 2; i++) {
                int cardX = x + 14 + i * (SMALL_WIDTH + 4);
                if (reveal) {
                    drawSmallCard(cardX, SEAT_Y + 25, cards[HOLE + seat * 2 + i]);
                } else {
                    drawSmallBack(cardX, SEAT_Y + 25);
                }
            }
        }
        TftTouchShield.setCursor(x + 4, SEAT_Y + 62);
        drawActionText(seats, seat, SEAT_BACKGROUND, reveal, cards, work);
    }

    private static void drawPlayer(byte[] cards, int[] seats, int[] work, boolean reveal) {
        TftTouchShield.fillRect(0, PLAYER_Y - 2, WIDTH, ROW1_Y - PLAYER_Y, FELT);
        int state = seats[STATE];
        if (toAct == 0) {
            TftTouchShield.drawRect(4, PLAYER_Y - 2, 2 * CARD_WIDTH + 12, CARD_HEIGHT + 4, HIGHLIGHT);
        }
        if (state != OUT && state != FOLDED && handNumber > 0) {
            drawCard(8, PLAYER_Y, cards[HOLE]);
            drawCard(8 + CARD_WIDTH + 4, PLAYER_Y, cards[HOLE + 1]);
        }
        int x = 2 * CARD_WIDTH + 22;
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(CHIP_TEXT, FELT);
        TftTouchShield.setCursor(x, PLAYER_Y + 2);
        TftTouchShield.print(seats[CHIPS]);
        if (dealer == 0 && handNumber > 0) {
            drawDealerButton(WIDTH - 12, PLAYER_Y + 8);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(x, PLAYER_Y + 24);
        drawActionText(seats, 0, FELT, reveal, cards, work);
        if (state != OUT && state != FOLDED && boardCount >= 3) {
            TftTouchShield.setTextColor(TftTouchShield.CYAN, FELT);
            TftTouchShield.setCursor(x, PLAYER_Y + 40);
            TftTouchShield.print(categoryName(scoreSeat(cards, work, 0, boardCount)));
        }
    }

    private static void drawActionText(int[] seats, int seat, int background, boolean reveal, byte[] cards,
                                       int[] work) {
        int action = seats[ACTION + seat];
        int bet = seats[BET + seat];
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, background);
        if (action == WINNER) {
            TftTouchShield.setTextColor(TftTouchShield.GREEN, background);
            TftTouchShield.print("WINS");
        } else if (seats[STATE + seat] == ALL_IN) {
            TftTouchShield.setTextColor(TftTouchShield.ORANGE, background);
            TftTouchShield.print("ALL IN ");
            TftTouchShield.print(seats[TOTAL + seat]);
        } else if (action == FOLD) {
            TftTouchShield.setTextColor(TftTouchShield.GRAY, background);
            TftTouchShield.print("Fold");
        } else if (action == CHECK) {
            TftTouchShield.print("Check");
        } else if (action == CALL) {
            TftTouchShield.print("Call ");
            TftTouchShield.print(bet);
        } else if (action == RAISE) {
            TftTouchShield.setTextColor(TftTouchShield.ORANGE, background);
            TftTouchShield.print("Raise ");
            TftTouchShield.print(bet);
        } else if (action == SMALL_BLIND) {
            TftTouchShield.print("SB ");
            TftTouchShield.print(bet);
        } else if (action == BIG_BLIND) {
            TftTouchShield.print("BB ");
            TftTouchShield.print(bet);
        }
    }

    private static void drawDealerButton(int cx, int cy) {
        TftTouchShield.fillCircle(cx, cy, 6, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, TftTouchShield.WHITE);
        TftTouchShield.setCursor(cx - 2, cy - 3);
        TftTouchShield.print("D");
    }

    private static void showWinner(int seat, int amount, int score) {
        if (seat < 0) {
            return;
        }
        TftTouchShield.fillRect(0, MESSAGE_Y - 2, WIDTH, 14, FELT);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GREEN, FELT);
        TftTouchShield.setCursor(8, MESSAGE_Y);
        TftTouchShield.print(seatName(seat));
        if (seat == 0) {
            TftTouchShield.print(" win ");
        } else {
            TftTouchShield.print(" wins ");
        }
        TftTouchShield.print(amount);
        if (score >= 0) {
            TftTouchShield.print(" - ");
            TftTouchShield.print(categoryName(score));
        }
    }

    private static void showMessage(String text, int color) {
        TftTouchShield.fillRect(0, MESSAGE_Y - 2, WIDTH, 14, FELT);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(color, FELT);
        TftTouchShield.setCursor(8, MESSAGE_Y);
        TftTouchShield.print(text);
    }

    private static String seatName(int seat) {
        if (seat == 0) {
            return "You";
        }
        if (seat == 1) {
            return "Ann";
        }
        if (seat == 2) {
            return "Bob";
        }
        return "Cal";
    }

    private static void clearControls() {
        TftTouchShield.fillRect(0, ROW1_Y - 2, WIDTH, 320 - ROW1_Y + 2, HEADER_BACKGROUND);
    }

    private static void drawDealButtons(boolean dealEnabled) {
        clearControls();
        drawButton(1, ROW2_Y, "DEAL", dealEnabled);
    }

    private static void drawActionButtons(int[] seats, int toCall, boolean canRaise) {
        clearControls();
        drawSmallButton(4, 40, "-", canRaise);
        TftTouchShield.fillRect(48, ROW1_Y, 80, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(CHIP_TEXT, TftTouchShield.BLACK);
        TftTouchShield.setCursor(52, ROW1_Y + 7);
        if (canRaise) {
            TftTouchShield.print(raiseTo);
        }
        drawSmallButton(132, 40, "+", canRaise);
        drawSmallButton(176, 60, "ALL", canRaise);
        drawButton(0, ROW2_Y, "FOLD", toCall > 0);
        if (toCall == 0) {
            drawButton(1, ROW2_Y, "CHECK", true);
        } else {
            drawCallButton(Math.min(toCall, seats[CHIPS]));
        }
        if (currentBet == 0) {
            drawButton(2, ROW2_Y, "BET", canRaise);
        } else {
            drawButton(2, ROW2_Y, "RAISE", canRaise);
        }
    }

    private static void drawCallButton(int amount) {
        int x = 4 + 78;
        TftTouchShield.fillRect(x, ROW2_Y, 72, BUTTON_HEIGHT, BUTTON);
        TftTouchShield.drawRect(x, ROW2_Y, 72, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, BUTTON);
        TftTouchShield.setCursor(x + 6, ROW2_Y + 10);
        TftTouchShield.print("CALL ");
        TftTouchShield.print(amount);
    }

    private static void drawButton(int index, int y, String label, boolean enabled) {
        int x = 4 + index * 78;
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        TftTouchShield.fillRect(x, y, 72, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(x, y, 72, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(x + (72 - label.length() * 12) / 2, y + 7);
        TftTouchShield.print(label);
    }

    private static void drawSmallButton(int x, int width, String label, boolean enabled) {
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        TftTouchShield.fillRect(x, ROW1_Y, width, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(x, ROW1_Y, width, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(x + (width - label.length() * 12) / 2, ROW1_Y + 7);
        TftTouchShield.print(label);
    }

    private static void drawCard(int x, int y, int card) {
        int suit = card / 13;
        int ink = inkOf(card);
        TftTouchShield.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.drawRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(ink, TftTouchShield.WHITE);
        TftTouchShield.setCursor(x + 3, y + 3);
        TftTouchShield.print(rankName(card % 13));
        drawSuit(x + 4, y + 21, suit, 1, ink);
        drawSuit(x + CARD_WIDTH - 22, y + CARD_HEIGHT - 22, suit, 2, ink);
    }

    private static void drawSmallCard(int x, int y, int card) {
        int ink = inkOf(card);
        TftTouchShield.fillRect(x, y, SMALL_WIDTH, SMALL_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.drawRect(x, y, SMALL_WIDTH, SMALL_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(ink, TftTouchShield.WHITE);
        TftTouchShield.setCursor(x + 3, y + 3);
        TftTouchShield.print(rankName(card % 13));
        drawSuit(x + 7, y + 16, card / 13, 1, ink);
    }

    private static void drawSmallBack(int x, int y) {
        TftTouchShield.fillRect(x, y, SMALL_WIDTH, SMALL_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.fillRect(x + 2, y + 2, SMALL_WIDTH - 4, SMALL_HEIGHT - 4, CARD_BACK);
        TftTouchShield.drawRect(x + 5, y + 5, SMALL_WIDTH - 10, SMALL_HEIGHT - 10, TftTouchShield.WHITE);
    }

    private static int inkOf(int card) {
        int suit = card / 13;
        if (suit == 1 || suit == 2) {
            return TftTouchShield.RED;
        }
        return TftTouchShield.BLACK;
    }

    // Streams a 9x9 suit bitmap scaled by {@code scale}, on white.
    private static void drawSuit(int x, int y, int suit, int scale, int ink) {
        int size = 9 * scale;
        if (!TftTouchShield.beginPixels(x, y, size, size)) {
            return;
        }
        for (int row = 0; row < 9; row++) {
            int bits = suitRow(suit, row);
            for (int repeatRow = 0; repeatRow < scale; repeatRow++) {
                for (int column = 0; column < 9; column++) {
                    int color = TftTouchShield.WHITE;
                    if (((bits >> column) & 1) != 0) {
                        color = ink;
                    }
                    for (int repeatColumn = 0; repeatColumn < scale; repeatColumn++) {
                        TftTouchShield.pushPixel(color);
                    }
                }
            }
        }
    }

    private static String rankName(int rank) {
        if (rank == 0) {
            return "A";
        }
        if (rank == 9) {
            return "10";
        }
        if (rank == 10) {
            return "J";
        }
        if (rank == 11) {
            return "Q";
        }
        if (rank == 12) {
            return "K";
        }
        return String.valueOf(rank + 1);
    }

    // 9x9 suit symbols (as in Blackjack), bit n = column n.
    private static int suitRow(int suit, int row) {
        if (suit == 0) {
            if (row == 0 || row == 7) {
                return 0x010;
            }
            if (row == 1 || row == 8) {
                return 0x038;
            }
            if (row == 2) {
                return 0x07C;
            }
            if (row == 3) {
                return 0x0FE;
            }
            if (row == 4 || row == 5) {
                return 0x1FF;
            }
            return 0x0D6;
        }
        if (suit == 1) {
            if (row == 0) {
                return 0x0C6;
            }
            if (row == 1) {
                return 0x1EF;
            }
            if (row == 2 || row == 3) {
                return 0x1FF;
            }
            if (row == 4) {
                return 0x0FE;
            }
            if (row == 5) {
                return 0x07C;
            }
            if (row == 6) {
                return 0x038;
            }
            if (row == 7) {
                return 0x010;
            }
            return 0;
        }
        if (suit == 2) {
            if (row == 0 || row == 8) {
                return 0x010;
            }
            if (row == 1 || row == 7) {
                return 0x038;
            }
            if (row == 2 || row == 6) {
                return 0x07C;
            }
            if (row == 3 || row == 5) {
                return 0x0FE;
            }
            return 0x1FF;
        }
        if (row == 0 || row == 2 || row == 7) {
            return 0x038;
        }
        if (row == 1) {
            return 0x07C;
        }
        if (row == 3 || row == 5) {
            return 0x0D6;
        }
        if (row == 4) {
            return 0x1FF;
        }
        if (row == 6) {
            return 0x010;
        }
        return 0;
    }
}
