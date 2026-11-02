package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

import static io.github.jabrena.juno.games.texasholdem.SuitSprites.suitRow;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CARD_BACK;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CARD_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CARD_WIDTH;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SMALL_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SMALL_WIDTH;

final class CardsRenderer {
    private CardsRenderer() {
    }

    static void drawCard(TftTouchShield tft, int x, int y, int card) {
        int suit = card / 13;
        int ink = inkOf(card);
        tft.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        tft.drawRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.GRAY);
        tft.setTextSize(2);
        tft.setTextColor(ink, TftTouchShield.WHITE);
        tft.setCursor(x + 3, y + 3);
        tft.print(rankName(card % 13));
        drawSuit(tft, x + 4, y + 21, suit, 1, ink);
        drawSuit(tft, x + CARD_WIDTH - 22, y + CARD_HEIGHT - 22, suit, 2, ink);
    }

    static void drawSmallCard(TftTouchShield tft, int x, int y, int card) {
        int ink = inkOf(card);
        tft.fillRect(x, y, SMALL_WIDTH, SMALL_HEIGHT, TftTouchShield.WHITE);
        tft.drawRect(x, y, SMALL_WIDTH, SMALL_HEIGHT, TftTouchShield.GRAY);
        tft.setTextSize(1);
        tft.setTextColor(ink, TftTouchShield.WHITE);
        tft.setCursor(x + 3, y + 3);
        tft.print(rankName(card % 13));
        drawSuit(tft, x + 7, y + 16, card / 13, 1, ink);
    }

    static void drawSmallBack(TftTouchShield tft, int x, int y) {
        tft.fillRect(x, y, SMALL_WIDTH, SMALL_HEIGHT, TftTouchShield.WHITE);
        tft.fillRect(x + 2, y + 2, SMALL_WIDTH - 4, SMALL_HEIGHT - 4, CARD_BACK);
        tft.drawRect(x + 5, y + 5, SMALL_WIDTH - 10, SMALL_HEIGHT - 10, TftTouchShield.WHITE);
    }

    static int inkOf(int card) {
        int suit = card / 13;
        if (suit == 1 || suit == 2) {
            return TftTouchShield.RED;
        }
        return TftTouchShield.BLACK;
    }

    static void drawSuit(TftTouchShield tft, int x, int y, int suit, int scale, int ink) {
        int size = 9 * scale;
        if (!tft.beginPixels(x, y, size, size)) {
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
                        tft.pushPixel(color);
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
