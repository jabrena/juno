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

final class ClubSprite {
    private ClubSprite() {
    }

    static int clubRow(int row) {
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
