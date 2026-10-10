package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.api.tft.TftTouchShield;

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

final class SceneRenderer {
    private SceneRenderer() {
    }

    static void drawHeader() {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 8);
        TftTouchShield.print("Bank ");
        TftTouchShield.print(bank);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(144, 8);
        TftTouchShield.print("Bet ");
        if (phase == PLAYING) {
            TftTouchShield.print(handBet);
        } else {
            TftTouchShield.print(bet);
        }
    }

    static void showMessage(String text, int color) {
        TftTouchShield.fillRect(0, MESSAGE_Y, TftTouchShield.width(), 24, FELT);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, FELT);
        TftTouchShield.setCursor((TftTouchShield.width() - text.length() * 12) / 2, MESSAGE_Y + 4);
        TftTouchShield.print(text);
    }

    static void drawButtons(byte[] player) {
        if (phase == BETTING) {
            drawButton(0, "-", bet > MIN_BET);
            drawButton(1, "+", bet < bank);
            drawButton(2, "DEAL", true);
        } else {
            drawButton(0, "HIT", true);
            drawButton(1, "STAND", true);
            drawButton(2, "DBL", canDouble());
        }
    }

    static void drawButton(int index, String label, boolean enabled) {
        int x = HAND_X + index * BUTTON_SPACING;
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        TftTouchShield.fillRect(x, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(x, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(x + (BUTTON_WIDTH - label.length() * 12) / 2, BUTTON_Y + 14);
        TftTouchShield.print(label);
    }

    static void clearHands() {
        TftTouchShield.fillRect(0, DEALER_LABEL_Y, TftTouchShield.width(), MESSAGE_Y - DEALER_LABEL_Y, FELT);
        TftTouchShield.fillRect(0, PLAYER_LABEL_Y, TftTouchShield.width(), BUTTON_Y - PLAYER_LABEL_Y - 4, FELT);
    }

    static void drawHand(byte[] cards, int count, boolean hideSecond, int labelY, int cardsY, String name) {
        TftTouchShield.fillRect(0, labelY, TftTouchShield.width(), cardsY + CARD_HEIGHT - labelY, FELT);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, FELT);
        TftTouchShield.setCursor(HAND_X, labelY);
        TftTouchShield.print(name);
        TftTouchShield.print(" ");
        if (hideSecond) {
            TftTouchShield.print(handValue(cards, 1));
            TftTouchShield.print(" + ?");
        } else {
            TftTouchShield.print(handValue(cards, count));
        }

        int step = CARD_WIDTH + 4;
        if (count > 1) {
            step = Math.min(step, (TftTouchShield.width() - 2 * HAND_X - CARD_WIDTH) / (count - 1));
        }
        for (int i = 0; i < count; i++) {
            int x = HAND_X + i * step;
            if (hideSecond && i == 1) {
                drawCardBack(x, cardsY);
            } else {
                drawCard(x, cardsY, cards[i]);
            }
        }
    }

    static void drawCard(int x, int y, int card) {
        int rank = card % 13;
        int suit = card / 13;
        int ink = TftTouchShield.BLACK;
        if (suit == HEARTS || suit == DIAMONDS) {
            ink = TftTouchShield.RED;
        }
        TftTouchShield.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.drawRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(ink, TftTouchShield.WHITE);
        TftTouchShield.setCursor(x + 3, y + 3);
        TftTouchShield.print(rankName(rank));
        drawSuit(x + 4, y + 21, suit, 1, ink);
        drawSuit(x + CARD_WIDTH - 22, y + CARD_HEIGHT - 22, suit, 2, ink);
    }

    static void drawCardBack(int x, int y) {
        TftTouchShield.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.fillRect(x + 3, y + 3, CARD_WIDTH - 6, CARD_HEIGHT - 6, CARD_BACK);
        for (int i = 0; i < 4; i++) {
            TftTouchShield.drawRect(x + 6 + i * 4, y + 6 + i * 4, CARD_WIDTH - 12 - i * 8, CARD_HEIGHT - 12 - i * 8,
                    TftTouchShield.WHITE);
        }
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
