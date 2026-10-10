package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.api.Random;

import static io.github.jabrena.juno.games.blackjack.Blackjack.*;
import static io.github.jabrena.juno.games.blackjack.Cards.*;
import static io.github.jabrena.juno.games.blackjack.ClubSprite.*;
import static io.github.jabrena.juno.games.blackjack.Controls.*;
import static io.github.jabrena.juno.games.blackjack.DiamondSprite.*;
import static io.github.jabrena.juno.games.blackjack.HeartSprite.*;
import static io.github.jabrena.juno.games.blackjack.Round.*;
import static io.github.jabrena.juno.games.blackjack.SceneRenderer.*;
import static io.github.jabrena.juno.games.blackjack.SpadeSprite.*;
import static io.github.jabrena.juno.games.blackjack.SuitSprites.*;

final class Cards {
    private Cards() {
    }

    static void shuffle(byte[] deck) {
        for (int i = 0; i < 52; i++) {
            deck[i] = (byte) i;
        }
        for (int i = 51; i > 0; i--) {
            int j = Random.nextInt(i + 1);
            byte swap = deck[i];
            deck[i] = deck[j];
            deck[j] = swap;
        }
        deckPosition = 0;
    }

    static byte draw(byte[] deck) {
        if (deckPosition == 52) {
            shuffle(deck);
        }
        byte card = deck[deckPosition];
        deckPosition = deckPosition + 1;
        return card;
    }

    static int handValue(byte[] cards, int count) {
        int total = 0;
        boolean ace = false;
        for (int i = 0; i < count; i++) {
            int rank = cards[i] % 13;
            if (rank == 0) {
                ace = true;
                total = total + 1;
            } else if (rank >= 9) {
                total = total + 10;
            } else {
                total = total + rank + 1;
            }
        }
        if (ace && total + 10 <= 21) {
            total = total + 10;
        }
        return total;
    }
}
