package io.github.jabrena.juno.api.tft;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Rules and invariants of Texas Hold'em, the slot machine and Solitaire. */
class CardGamesTest {
    @AfterEach
    void releaseThePanel() {
        Internals.stopTapping();
    }

    // ---- Texas Hold'em ----

    @Test
    void holdemRanksHandsLikeABruteForceReference() {
        java.util.Random random = new java.util.Random(3);
        int[] work = new int[84];
        int hands = 5000;
        int[] scores = new int[hands];
        long[] reference = new long[hands];
        int[] categories = new int[9];
        for (int hand = 0; hand < hands; hand++) {
            int[] cards = randomSeven(random, hand);
            for (int i = 0; i < 7; i++) {
                work[17 + i] = cards[i];
            }
            scores[hand] = callInt(TexasHoldem.class, "evaluate", work, 7);
            reference[hand] = bestOfSeven(cards);
            categories[scores[hand] >> 20] = categories[scores[hand] >> 20] + 1;
            assertThat(scores[hand] >> 20).as("category of %s", Arrays.toString(cards))
                    .isEqualTo((int) (reference[hand] >> 40));
        }
        for (int i = 0; i < 20000; i++) {
            int a = random.nextInt(hands);
            int b = random.nextInt(hands);
            assertThat(Integer.signum(Integer.compare(scores[a], scores[b])))
                    .isEqualTo(Long.signum(Long.compare(reference[a], reference[b])));
        }
        assertThat(categories).as("every category was exercised").doesNotContain(0);
    }

    @Test
    void holdemEquityEstimatesArePlausible() {
        Random.seed(1);
        byte[] cards = new byte[65];
        int[] work = new int[84];
        set(TexasHoldem.class, "boardCount", 0);
        cards[54] = 0;
        cards[55] = 13;
        float aces = (float) call(TexasHoldem.class, "equity", cards, work, 1, 1);
        cards[54] = 6;
        cards[55] = 14;
        float sevenTwo = (float) call(TexasHoldem.class, "equity", cards, work, 1, 1);
        assertThat(aces).as("pocket aces heads-up").isBetween(0.78f, 0.92f);
        assertThat(sevenTwo).as("seven-two offsuit heads-up").isBetween(0.25f, 0.42f);
    }

    /** Random taps on every control, including all-ins that create side pots: no chip may vanish. */
    @Test
    void holdemNeverLosesAChip() {
        Random.seed(100);
        java.util.Random taps = new java.util.Random(9);
        Internals.tapEvery(5, () -> {
            int pick = taps.nextInt(10);
            if (pick < 1) {
                return new int[] {30, 300};
            }
            if (pick < 6) {
                return new int[] {120, 300};
            }
            if (pick < 7) {
                return new int[] {200, 270};
            }
            if (pick < 8) {
                return new int[] {150, 270};
            }
            return new int[] {200, 300};
        });
        byte[] cards = new byte[65];
        int[] work = new int[84];
        int allIns = 0;
        for (int game = 0; game < 8; game++) {
            int[] seats = new int[24];
            call(TexasHoldem.class, "newGame", seats);
            for (int hand = 0; hand < 60; hand++) {
                call(TexasHoldem.class, "playHand", cards, seats, work);
                int total = 0;
                for (int seat = 0; seat < 4; seat++) {
                    assertThat(seats[seat]).isNotNegative();
                    assertThat(seats[8 + seat]).as("pot paid out").isZero();
                    total = total + seats[seat];
                    if (seats[12 + seat] == 2) {
                        allIns = allIns + 1;
                    }
                }
                assertThat(total).as("chips on the table, game %d hand %d", game, hand).isEqualTo(4000);
                if (seats[0] == 0 || callInt(TexasHoldem.class, "alive", (Object) seats) == 1) {
                    break;
                }
            }
        }
        assertThat(allIns).as("all-ins exercised side pots").isPositive();
    }

    // ---- Slot machine ----

    @Test
    void slotMachinePaysBackExactly89Point6Percent() {
        int paid = 0;
        for (int a = 0; a < 20; a++) {
            for (int b = 0; b < 20; b++) {
                for (int c = 0; c < 20; c++) {
                    paid = paid + callInt(SlotMachine.class, "payout",
                            callInt(SlotMachine.class, "symbol", 0, a),
                            callInt(SlotMachine.class, "symbol", 1, b),
                            callInt(SlotMachine.class, "symbol", 2, c));
                }
            }
        }
        assertThat(paid / 8000.0).isEqualTo(0.89625);
    }

    @Test
    void slotReelsStopWhereTheSpinDecidedAndCreditsAddUp() {
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT);
        set(SlotMachine.class, "seeded", true);
        set(SlotMachine.class, "credits", 100);
        set(SlotMachine.class, "bet", 1);
        int[] positions = new int[3];
        int[] ticks = new int[3];
        for (int spin = 0; spin < 60; spin++) {
            Random.seed(1000 + spin);
            java.util.Random same = new java.util.Random(1000 + spin);
            int[] stops = {same.nextInt(20), same.nextInt(20), same.nextInt(20)};
            if (getInt(SlotMachine.class, "credits") == 0) {
                set(SlotMachine.class, "credits", 100);
            }
            int before = getInt(SlotMachine.class, "credits");
            call(SlotMachine.class, "spin", positions, ticks);
            assertThat(positions).isEqualTo(stops);
            assertThat(getInt(SlotMachine.class, "credits"))
                    .isEqualTo(before - 1 + getInt(SlotMachine.class, "lastWin"));
        }
    }

    // ---- Solitaire ----

    @Test
    void solitaireKeepsEveryRuleWhileABotPlays() {
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT);
        int wins = 0;
        for (int game = 0; game < 60; game++) {
            Random.seed(game);
            byte[] cards = new byte[13 * 24];
            int[] meta = new int[20];
            call(Solitaire.class, "deal", cards, meta, new byte[52]);
            assertSolitaireInvariants(cards, meta);
            int idle = 0;
            for (int step = 0; step < 3000 && !callBoolean(Solitaire.class, "won", meta); step++) {
                if (solitaireBotMove(cards, meta)) {
                    idle = 0;
                } else {
                    if (meta[11] == 0 && meta[12] == 0) {
                        break;
                    }
                    if (meta[11] == 0 && ++idle > 2) {
                        break;
                    }
                    call(Solitaire.class, "turnStock", cards, meta);
                }
                assertSolitaireInvariants(cards, meta);
            }
            if (callBoolean(Solitaire.class, "won", meta)) {
                wins = wins + 1;
            }
        }
        assertThat(wins).as("games won, which exercises the automatic finish").isPositive();
    }

    /** Foundations first, then tableau moves that uncover cards, then the waste. */
    private static boolean solitaireBotMove(byte[] cards, int[] meta) {
        for (int pile : new int[] {12, 0, 1, 2, 3, 4, 5, 6}) {
            if (meta[pile] > 0) {
                int card = cards[pile * 24 + meta[pile] - 1];
                if (tryMove(cards, meta, pile, meta[pile] - 1, 7 + card / 13)) {
                    return true;
                }
            }
        }
        for (int from = 0; from < 7; from++) {
            int hidden = meta[13 + from];
            boolean kingAlreadyAtBottom = hidden == 0 && meta[from] > 0 && cards[from * 24] % 13 == 12;
            if (meta[from] == 0 || kingAlreadyAtBottom) {
                continue;
            }
            for (int to = 0; to < 7; to++) {
                if (tryMove(cards, meta, from, hidden, to)) {
                    return true;
                }
            }
        }
        for (int to = 0; to < 7 && meta[12] > 0; to++) {
            if (tryMove(cards, meta, 12, meta[12] - 1, to)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryMove(byte[] cards, int[] meta, int from, int index, int to) {
        if (!callBoolean(Solitaire.class, "move", cards, meta, from, index, to)) {
            return false;
        }
        call(Solitaire.class, "checkEnd", cards, meta);
        return true;
    }

    private static void assertSolitaireInvariants(byte[] cards, int[] meta) {
        Set<Integer> seen = new HashSet<>();
        for (int pile = 0; pile < 13; pile++) {
            for (int i = 0; i < meta[pile]; i++) {
                assertThat(seen.add((int) cards[pile * 24 + i])).as("card appears once").isTrue();
            }
        }
        assertThat(seen).hasSize(52);
        for (int suit = 0; suit < 4; suit++) {
            for (int i = 0; i < meta[7 + suit]; i++) {
                assertThat((int) cards[(7 + suit) * 24 + i]).as("foundation order").isEqualTo(suit * 13 + i);
            }
        }
        for (int column = 0; column < 7; column++) {
            assertThat(meta[13 + column] < meta[column] || meta[column] == 0).as("top card face up").isTrue();
            for (int i = meta[13 + column] + 1; i < meta[column]; i++) {
                int below = cards[column * 24 + i - 1];
                int above = cards[column * 24 + i];
                assertThat(below % 13).isEqualTo(above % 13 + 1);
                assertThat(isRed(below)).isNotEqualTo(isRed(above));
            }
        }
    }

    private static boolean isRed(int card) {
        return card / 13 == 1 || card / 13 == 2;
    }

    // ---- A brute-force poker reference: the best five of seven, compared as a long ----

    private static int[] randomSeven(java.util.Random random, int hand) {
        while (true) {
            List<Integer> deck = new ArrayList<>();
            for (int card = 0; card < 52; card++) {
                deck.add(card);
            }
            Collections.shuffle(deck, random);
            int[] cards = new int[7];
            for (int i = 0; i < 7; i++) {
                cards[i] = deck.get(i);
            }
            if (hand % 10 == 1) {
                // A straight flush in a random suit.
                int suit = random.nextInt(4);
                int top = random.nextInt(9) + 4;
                for (int i = 0; i < 5; i++) {
                    int value = (top - i + 13) % 13;
                    cards[i] = suit * 13 + (value == 12 ? 0 : value + 1);
                }
            } else if (hand % 10 == 2) {
                int rank = random.nextInt(13);
                for (int i = 0; i < 4; i++) {
                    cards[i] = i * 13 + rank;
                }
            }
            if (Arrays.stream(cards).distinct().count() == 7) {
                return cards;
            }
        }
    }

    private static long bestOfSeven(int[] cards) {
        long best = -1;
        int[] five = new int[5];
        for (int skipA = 0; skipA < 7; skipA++) {
            for (int skipB = skipA + 1; skipB < 7; skipB++) {
                int k = 0;
                for (int i = 0; i < 7; i++) {
                    if (i != skipA && i != skipB) {
                        five[k] = cards[i];
                        k = k + 1;
                    }
                }
                best = Math.max(best, rankFive(five));
            }
        }
        return best;
    }

    /** Category in bits 40+, then the ranks by (count, rank) descending. */
    private static long rankFive(int[] cards) {
        int[] values = new int[5];
        int[] counts = new int[13];
        boolean flush = true;
        for (int i = 0; i < 5; i++) {
            int rank = cards[i] % 13;
            values[i] = rank == 0 ? 12 : rank - 1;
            counts[values[i]] = counts[values[i]] + 1;
            flush = flush && cards[i] / 13 == cards[0] / 13;
        }
        Integer[] order = new Integer[13];
        for (int i = 0; i < 13; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> counts[b] != counts[a] ? counts[b] - counts[a] : b - a);
        int distinct = (int) Arrays.stream(counts).filter(count -> count > 0).count();
        int straightTop = -1;
        if (distinct == 5) {
            int[] sorted = values.clone();
            Arrays.sort(sorted);
            if (sorted[4] - sorted[0] == 4) {
                straightTop = sorted[4];
            } else if (sorted[4] == 12 && sorted[3] == 3) {
                straightTop = 3;
            }
        }
        int category;
        if (straightTop >= 0 && flush) {
            category = 8;
        } else if (counts[order[0]] == 4) {
            category = 7;
        } else if (counts[order[0]] == 3 && counts[order[1]] == 2) {
            category = 6;
        } else if (flush) {
            category = 5;
        } else if (straightTop >= 0) {
            category = 4;
        } else if (counts[order[0]] == 3) {
            category = 3;
        } else if (counts[order[0]] == 2 && counts[order[1]] == 2) {
            category = 2;
        } else if (counts[order[0]] == 2) {
            category = 1;
        } else {
            category = 0;
        }
        long key = 0;
        if (straightTop >= 0 && (category == 8 || category == 4)) {
            key = straightTop;
        } else {
            for (int i = 0; i < 5; i++) {
                key = key * 16 + (counts[order[i]] > 0 ? order[i] : 0);
            }
        }
        return ((long) category << 40) + key;
    }
}
