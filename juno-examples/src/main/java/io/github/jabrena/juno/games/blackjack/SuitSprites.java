package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.blackjack.Blackjack.*;
import static io.github.jabrena.juno.games.blackjack.Round.*;
import static io.github.jabrena.juno.games.blackjack.Cards.*;
import static io.github.jabrena.juno.games.blackjack.Controls.*;
import static io.github.jabrena.juno.games.blackjack.SceneRenderer.*;
import static io.github.jabrena.juno.games.blackjack.SuitSprites.*;
import static io.github.jabrena.juno.games.blackjack.SpadeSprite.*;
import static io.github.jabrena.juno.games.blackjack.HeartSprite.*;
import static io.github.jabrena.juno.games.blackjack.DiamondSprite.*;
import static io.github.jabrena.juno.games.blackjack.ClubSprite.*;

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
