package io.github.jabrena.juno.games.blackjack;

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
