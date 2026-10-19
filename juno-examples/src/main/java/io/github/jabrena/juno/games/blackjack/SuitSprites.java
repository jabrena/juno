package io.github.jabrena.juno.games.blackjack;

import static io.github.jabrena.juno.games.blackjack.Blackjack.DIAMONDS;
import static io.github.jabrena.juno.games.blackjack.Blackjack.HEARTS;
import static io.github.jabrena.juno.games.blackjack.Blackjack.SPADES;
import static io.github.jabrena.juno.games.blackjack.ClubSprite.clubRow;
import static io.github.jabrena.juno.games.blackjack.DiamondSprite.diamondRow;
import static io.github.jabrena.juno.games.blackjack.HeartSprite.heartRow;
import static io.github.jabrena.juno.games.blackjack.SpadeSprite.spadeRow;

final class SuitSprites {
    private SuitSprites() {
    }

    static int suitRow(int suit, int row) {
        if (suit == SPADES) {
            return spadeRow(row);
        }
        if (suit == HEARTS) {
            return heartRow(row);
        }
        if (suit == DIAMONDS) {
            return diamondRow(row);
        }
        return clubRow(row);
    }
}
