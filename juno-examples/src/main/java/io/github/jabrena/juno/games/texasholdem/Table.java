package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.texasholdem.CardsRenderer.*;
import static io.github.jabrena.juno.games.texasholdem.Controls.*;
import static io.github.jabrena.juno.games.texasholdem.Deck.*;
import static io.github.jabrena.juno.games.texasholdem.HandEvaluator.*;
import static io.github.jabrena.juno.games.texasholdem.Players.*;
import static io.github.jabrena.juno.games.texasholdem.SceneRenderer.*;
import static io.github.jabrena.juno.games.texasholdem.SuitSprites.*;
import static io.github.jabrena.juno.games.texasholdem.Table.*;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.*;

final class Table {
    private Table() {
    }

    static void newGame(int[] seats) {
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

    static void playHand(byte[] cards, int[] seats, int[] work) {
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

    static boolean bettingRound(byte[] cards, int[] seats, int[] work, int first) {
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
                if (seat == 0 && !Controls.autopilot) {
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

    static boolean roundComplete(int[] seats) {
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

    static void pay(int[] seats, int seat, int amount) {
        int paid = Math.min(amount, seats[CHIPS + seat]);
        seats[CHIPS + seat] = seats[CHIPS + seat] - paid;
        seats[BET + seat] = seats[BET + seat] + paid;
        seats[TOTAL + seat] = seats[TOTAL + seat] + paid;
        if (seats[CHIPS + seat] == 0) {
            seats[STATE + seat] = ALL_IN;
        }
    }

    static void fold(int[] seats, int seat) {
        seats[STATE + seat] = FOLDED;
        seats[ACTION + seat] = FOLD;
    }

    static void checkOrCall(int[] seats, int seat) {
        if (seats[BET + seat] >= currentBet) {
            seats[ACTION + seat] = CHECK;
        } else {
            pay(seats, seat, currentBet - seats[BET + seat]);
            seats[ACTION + seat] = CALL;
        }
        seats[ACTED + seat] = 1;
    }

    static void raise(int[] seats, int seat, int target) {
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

    static void winByFold(byte[] cards, int[] seats, int[] work) {
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

    static void showdown(byte[] cards, int[] seats, int[] work) {
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
}
