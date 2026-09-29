package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.*;
import static io.github.jabrena.juno.games.texasholdem.Table.*;
import static io.github.jabrena.juno.games.texasholdem.Players.*;
import static io.github.jabrena.juno.games.texasholdem.HandEvaluator.*;
import static io.github.jabrena.juno.games.texasholdem.Deck.*;
import static io.github.jabrena.juno.games.texasholdem.Controls.*;
import static io.github.jabrena.juno.games.texasholdem.SceneRenderer.*;
import static io.github.jabrena.juno.games.texasholdem.CardsRenderer.*;
import static io.github.jabrena.juno.games.texasholdem.SuitSprites.*;

final class HandEvaluator {
    private HandEvaluator() {
    }

    static int scoreSeat(byte[] cards, int[] work, int seat, int shown) {
        work[HAND] = cards[HOLE + seat * 2];
        work[HAND + 1] = cards[HOLE + seat * 2 + 1];
        for (int i = 0; i < shown; i++) {
            work[HAND + 2 + i] = cards[BOARD + i];
        }
        return evaluate(work, 2 + shown);
    }

    static int evaluate(int[] work, int n) {
        int ranks = countCards(work, n);
        int flushSuit = findFlushSuit(work);
        int flushRanks = collectFlushRanks(work, n, flushSuit);
        int straightFlush = straightTop(flushRanks);
        if (straightFlush >= 0) {
            return 8 * CATEGORY + (straightFlush << 16);
        }
        return scoreByMultiples(work, ranks, flushSuit, flushRanks);
    }

    private static int countCards(int[] work, int n) {
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
        return ranks;
    }

    private static int findFlushSuit(int[] work) {
        int flushSuit = -1;
        for (int suit = 0; suit < 4; suit++) {
            if (work[SUITS + suit] >= 5) {
                flushSuit = suit;
            }
        }
        return flushSuit;
    }

    private static int collectFlushRanks(int[] work, int n, int flushSuit) {
        int flushRanks = 0;
        if (flushSuit < 0) {
            return flushRanks;
        }
        for (int i = 0; i < n; i++) {
            if (work[HAND + i] / 13 == flushSuit) {
                flushRanks = flushRanks | (1 << value(work[HAND + i]));
            }
        }
        return flushRanks;
    }

    private static int scoreByMultiples(int[] work, int ranks, int flushSuit, int flushRanks) {
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

    static int value(int card) {
        int rank = card % 13;
        if (rank == 0) {
            return 12;
        }
        return rank - 1;
    }

    static int topRanks(int mask, int count) {
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

    static int straightTop(int mask) {
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

    static String categoryName(int score) {
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
}
