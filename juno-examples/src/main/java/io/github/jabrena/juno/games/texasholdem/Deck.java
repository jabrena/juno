package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.api.util.Random;

import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CHIPS;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.DECK;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.OUT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SEATS;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.STATE;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.TOTAL;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.deckPosition;

final class Deck {
    private Deck() {
    }

    static void shuffle(byte[] cards) {
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

    static int nextSeat(int[] seats, int seat, boolean skipOut) {
        for (int i = 1; i <= SEATS; i++) {
            int next = (seat + i) % SEATS;
            if (!skipOut || seats[STATE + next] != OUT) {
                return next;
            }
        }
        return -1;
    }

    static int alive(int[] seats) {
        int count = 0;
        for (int seat = 0; seat < SEATS; seat++) {
            if (seats[CHIPS + seat] > 0 || seats[TOTAL + seat] > 0) {
                count = count + 1;
            }
        }
        return count;
    }

    static int countState(int[] seats, int state) {
        int count = 0;
        for (int seat = 0; seat < SEATS; seat++) {
            if (seats[STATE + seat] == state) {
                count = count + 1;
            }
        }
        return count;
    }
}
