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

final class Players {
    private Players() {
    }

    static void humanTurn(byte[] cards, int[] seats, int[] work) {
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

    static void computerTurn(byte[] cards, int[] seats, int[] work, int seat) {
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

    static float equity(byte[] cards, int[] work, int seat, int opponents) {
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
}
