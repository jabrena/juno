package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

import static io.github.jabrena.juno.games.blackjack.Blackjack.BETTING;
import static io.github.jabrena.juno.games.blackjack.Blackjack.BUTTON;
import static io.github.jabrena.juno.games.blackjack.Blackjack.BUTTON_DISABLED;
import static io.github.jabrena.juno.games.blackjack.Blackjack.BUTTON_HEIGHT;
import static io.github.jabrena.juno.games.blackjack.Blackjack.BUTTON_SPACING;
import static io.github.jabrena.juno.games.blackjack.Blackjack.BUTTON_WIDTH;
import static io.github.jabrena.juno.games.blackjack.Blackjack.BUTTON_Y;
import static io.github.jabrena.juno.games.blackjack.Blackjack.CARD_BACK;
import static io.github.jabrena.juno.games.blackjack.Blackjack.CARD_HEIGHT;
import static io.github.jabrena.juno.games.blackjack.Blackjack.CARD_WIDTH;
import static io.github.jabrena.juno.games.blackjack.Blackjack.DEALER_LABEL_Y;
import static io.github.jabrena.juno.games.blackjack.Blackjack.DIAMONDS;
import static io.github.jabrena.juno.games.blackjack.Blackjack.FELT;
import static io.github.jabrena.juno.games.blackjack.Blackjack.HAND_X;
import static io.github.jabrena.juno.games.blackjack.Blackjack.HEADER_BACKGROUND;
import static io.github.jabrena.juno.games.blackjack.Blackjack.HEADER_HEIGHT;
import static io.github.jabrena.juno.games.blackjack.Blackjack.HEARTS;
import static io.github.jabrena.juno.games.blackjack.Blackjack.MESSAGE_Y;
import static io.github.jabrena.juno.games.blackjack.Blackjack.MIN_BET;
import static io.github.jabrena.juno.games.blackjack.Blackjack.PLAYER_LABEL_Y;
import static io.github.jabrena.juno.games.blackjack.Blackjack.PLAYING;
import static io.github.jabrena.juno.games.blackjack.Blackjack.bank;
import static io.github.jabrena.juno.games.blackjack.Blackjack.bet;
import static io.github.jabrena.juno.games.blackjack.Blackjack.handBet;
import static io.github.jabrena.juno.games.blackjack.Blackjack.phase;
import static io.github.jabrena.juno.games.blackjack.Cards.handValue;
import static io.github.jabrena.juno.games.blackjack.Round.canDouble;
import static io.github.jabrena.juno.games.blackjack.SuitSprites.suitRow;

final class SceneRenderer {
    private SceneRenderer() {
    }

    static void drawHeader(TftTouchShield tft) {
        tft.fillRect(0, 0, tft.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        tft.setCursor(8, 8);
        tft.print("Bank ");
        tft.print(bank);
        tft.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        tft.setCursor(144, 8);
        tft.print("Bet ");
        if (phase == PLAYING) {
            tft.print(handBet);
        } else {
            tft.print(bet);
        }
    }

    static void showMessage(TftTouchShield tft, String text, int color) {
        tft.fillRect(0, MESSAGE_Y, tft.width(), 24, FELT);
        tft.setTextSize(2);
        tft.setTextColor(color, FELT);
        tft.setCursor((tft.width() - text.length() * 12) / 2, MESSAGE_Y + 4);
        tft.print(text);
    }

    static void drawButtons(TftTouchShield tft, byte[] player) {
        if (phase == BETTING) {
            drawButton(tft, 0, "-", bet > MIN_BET);
            drawButton(tft, 1, "+", bet < bank);
            drawButton(tft, 2, "DEAL", true);
        } else {
            drawButton(tft, 0, "HIT", true);
            drawButton(tft, 1, "STAND", true);
            drawButton(tft, 2, "DBL", canDouble());
        }
    }

    static void drawButton(TftTouchShield tft, int index, String label, boolean enabled) {
        int x = HAND_X + index * BUTTON_SPACING;
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        tft.fillRect(x, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, color);
        tft.drawRect(x, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.BLACK);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.BLACK, color);
        tft.setCursor(x + (BUTTON_WIDTH - label.length() * 12) / 2, BUTTON_Y + 14);
        tft.print(label);
    }

    static void clearHands(TftTouchShield tft) {
        tft.fillRect(0, DEALER_LABEL_Y, tft.width(), MESSAGE_Y - DEALER_LABEL_Y, FELT);
        tft.fillRect(0, PLAYER_LABEL_Y, tft.width(), BUTTON_Y - PLAYER_LABEL_Y - 4, FELT);
    }

    static void drawHand(TftTouchShield tft, byte[] cards, int count, boolean hideSecond, int labelY, int cardsY, String name) {
        tft.fillRect(0, labelY, tft.width(), cardsY + CARD_HEIGHT - labelY, FELT);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, FELT);
        tft.setCursor(HAND_X, labelY);
        tft.print(name);
        tft.print(" ");
        if (hideSecond) {
            tft.print(handValue(cards, 1));
            tft.print(" + ?");
        } else {
            tft.print(handValue(cards, count));
        }

        int step = CARD_WIDTH + 4;
        if (count > 1) {
            step = Math.min(step, (tft.width() - 2 * HAND_X - CARD_WIDTH) / (count - 1));
        }
        for (int i = 0; i < count; i++) {
            int x = HAND_X + i * step;
            if (hideSecond && i == 1) {
                drawCardBack(tft, x, cardsY);
            } else {
                drawCard(tft, x, cardsY, cards[i]);
            }
        }
    }

    static void drawCard(TftTouchShield tft, int x, int y, int card) {
        int rank = card % 13;
        int suit = card / 13;
        int ink = TftTouchShield.BLACK;
        if (suit == HEARTS || suit == DIAMONDS) {
            ink = TftTouchShield.RED;
        }
        tft.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        tft.drawRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.GRAY);
        tft.setTextSize(2);
        tft.setTextColor(ink, TftTouchShield.WHITE);
        tft.setCursor(x + 3, y + 3);
        tft.print(rankName(rank));
        drawSuit(tft, x + 4, y + 21, suit, 1, ink);
        drawSuit(tft, x + CARD_WIDTH - 22, y + CARD_HEIGHT - 22, suit, 2, ink);
    }

    static void drawCardBack(TftTouchShield tft, int x, int y) {
        tft.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        tft.fillRect(x + 3, y + 3, CARD_WIDTH - 6, CARD_HEIGHT - 6, CARD_BACK);
        for (int i = 0; i < 4; i++) {
            tft.drawRect(x + 6 + i * 4, y + 6 + i * 4, CARD_WIDTH - 12 - i * 8, CARD_HEIGHT - 12 - i * 8,
                    TftTouchShield.WHITE);
        }
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
