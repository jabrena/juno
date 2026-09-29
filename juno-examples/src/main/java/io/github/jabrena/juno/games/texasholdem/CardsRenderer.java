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

final class CardsRenderer {
    private CardsRenderer() {
    }

    static void drawCard(int x, int y, int card) {
        int suit = card / 13;
        int ink = inkOf(card);
        TftTouchShield.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.drawRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(ink, TftTouchShield.WHITE);
        TftTouchShield.setCursor(x + 3, y + 3);
        TftTouchShield.print(rankName(card % 13));
        drawSuit(x + 4, y + 21, suit, 1, ink);
        drawSuit(x + CARD_WIDTH - 22, y + CARD_HEIGHT - 22, suit, 2, ink);
    }

    static void drawSmallCard(int x, int y, int card) {
        int ink = inkOf(card);
        TftTouchShield.fillRect(x, y, SMALL_WIDTH, SMALL_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.drawRect(x, y, SMALL_WIDTH, SMALL_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(ink, TftTouchShield.WHITE);
        TftTouchShield.setCursor(x + 3, y + 3);
        TftTouchShield.print(rankName(card % 13));
        drawSuit(x + 7, y + 16, card / 13, 1, ink);
    }

    static void drawSmallBack(int x, int y) {
        TftTouchShield.fillRect(x, y, SMALL_WIDTH, SMALL_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.fillRect(x + 2, y + 2, SMALL_WIDTH - 4, SMALL_HEIGHT - 4, CARD_BACK);
        TftTouchShield.drawRect(x + 5, y + 5, SMALL_WIDTH - 10, SMALL_HEIGHT - 10, TftTouchShield.WHITE);
    }

    static int inkOf(int card) {
        int suit = card / 13;
        if (suit == 1 || suit == 2) {
            return TftTouchShield.RED;
        }
        return TftTouchShield.BLACK;
    }

    static void drawSuit(int x, int y, int suit, int scale, int ink) {
        int size = 9 * scale;
        if (!TftTouchShield.beginPixels(x, y, size, size)) {
            return;
        }
        for (int row = 0; row < 9; row++) {
            int bits = suitRow(suit, row);
            for (int repeatRow = 0; repeatRow < scale; repeatRow++) {
                for (int column = 0; column < 9; column++) {
                    int color = TftTouchShield.WHITE;
                    if (((bits >> column) & 1) != 0) {
                        color = ink;
                    }
                    for (int repeatColumn = 0; repeatColumn < scale; repeatColumn++) {
                        TftTouchShield.pushPixel(color);
                    }
                }
            }
        }
    }

    static String rankName(int rank) {
        if (rank == 0) {
            return "A";
        }
        if (rank == 9) {
            return "10";
        }
        if (rank == 10) {
            return "J";
        }
        if (rank == 11) {
            return "Q";
        }
        if (rank == 12) {
            return "K";
        }
        return String.valueOf(rank + 1);
    }
}
