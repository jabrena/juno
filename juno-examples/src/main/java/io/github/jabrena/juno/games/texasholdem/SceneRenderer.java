package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.texasholdem.CardsRenderer.*;
import static io.github.jabrena.juno.games.texasholdem.Controls.*;
import static io.github.jabrena.juno.games.texasholdem.Deck.*;
import static io.github.jabrena.juno.games.texasholdem.HandEvaluator.*;
import static io.github.jabrena.juno.games.texasholdem.Players.*;
import static io.github.jabrena.juno.games.texasholdem.SceneRenderer.*;
import static io.github.jabrena.juno.games.texasholdem.SuitSprites.*;
import static io.github.jabrena.juno.games.texasholdem.Table.*;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.*;

final class SceneRenderer {
    private SceneRenderer() {
    }

    static void drawTable(byte[] cards, int[] seats, int[] work, boolean reveal) {
        TftTouchShield.fillRect(0, HEADER_HEIGHT, WIDTH, ROW1_Y - HEADER_HEIGHT - 2, FELT);
        drawHeader();
        for (int seat = 0; seat < SEATS; seat++) {
            drawSeat(cards, seats, work, seat, reveal);
        }
        for (int i = 0; i < 5; i++) {
            int x = 8 + i * 46;
            if (i < boardCount) {
                drawCard(x, BOARD_Y, cards[BOARD + i]);
            } else {
                TftTouchShield.drawRect(x, BOARD_Y, CARD_WIDTH, CARD_HEIGHT, SEAT_BACKGROUND);
            }
        }
        drawPot(seats);
    }

    static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 5);
        TftTouchShield.print("HAND ");
        TftTouchShield.print(handNumber);
        TftTouchShield.print("   BLINDS ");
        TftTouchShield.print(smallBlind);
        TftTouchShield.print("/");
        TftTouchShield.print(smallBlind * 2);
        TftTouchShield.fillRect(NEW_X, 1, WIDTH - NEW_X - 2, HEADER_HEIGHT - 2, BUTTON);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, BUTTON);
        TftTouchShield.setCursor(NEW_X + 12, 5);
        TftTouchShield.print("NEW");
    }

    static void drawPot(int[] seats) {
        int pot = 0;
        for (int seat = 0; seat < SEATS; seat++) {
            pot = pot + seats[TOTAL + seat];
        }
        TftTouchShield.fillRect(0, POT_Y, WIDTH, 18, FELT);
        if (pot == 0) {
            return;
        }
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(CHIP_TEXT, FELT);
        int digits = String.valueOf(pot).length();
        TftTouchShield.setCursor((WIDTH - (4 + digits) * 12) / 2, POT_Y + 1);
        TftTouchShield.print("Pot ");
        TftTouchShield.print(pot);
    }

    static void drawSeat(byte[] cards, int[] seats, int[] work, int seat, boolean reveal) {
        int state = seats[STATE + seat];
        if (seat == 0) {
            drawPlayer(cards, seats, work, reveal);
            return;
        }
        int x = 2 + (seat - 1) * (SEAT_WIDTH + 1);
        int border = SEAT_BACKGROUND;
        if (toAct == seat) {
            border = HIGHLIGHT;
        } else if (seats[ACTION + seat] == WINNER) {
            border = TftTouchShield.GREEN;
        }
        TftTouchShield.fillRect(x, SEAT_Y, SEAT_WIDTH, SEAT_HEIGHT, SEAT_BACKGROUND);
        TftTouchShield.drawRect(x, SEAT_Y, SEAT_WIDTH, SEAT_HEIGHT, border);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SEAT_BACKGROUND);
        TftTouchShield.setCursor(x + 4, SEAT_Y + 4);
        TftTouchShield.print(seatName(seat));
        if (seat == dealer && state != OUT && handNumber > 0) {
            drawDealerButton(x + SEAT_WIDTH - 10, SEAT_Y + 7);
        }
        TftTouchShield.setTextColor(CHIP_TEXT, SEAT_BACKGROUND);
        TftTouchShield.setCursor(x + 4, SEAT_Y + 14);
        TftTouchShield.print(seats[CHIPS + seat]);
        if (state == OUT) {
            TftTouchShield.setTextColor(TftTouchShield.GRAY, SEAT_BACKGROUND);
            TftTouchShield.setCursor(x + 4, SEAT_Y + 36);
            TftTouchShield.print("OUT");
            return;
        }
        if (state != FOLDED) {
            for (int i = 0; i < 2; i++) {
                int cardX = x + 14 + i * (SMALL_WIDTH + 4);
                if (reveal) {
                    drawSmallCard(cardX, SEAT_Y + 25, cards[HOLE + seat * 2 + i]);
                } else {
                    drawSmallBack(cardX, SEAT_Y + 25);
                }
            }
        }
        TftTouchShield.setCursor(x + 4, SEAT_Y + 62);
        drawActionText(seats, seat, SEAT_BACKGROUND, reveal, cards, work);
    }

    static void drawPlayer(byte[] cards, int[] seats, int[] work, boolean reveal) {
        TftTouchShield.fillRect(0, PLAYER_Y - 2, WIDTH, ROW1_Y - PLAYER_Y, FELT);
        int state = seats[STATE];
        if (toAct == 0) {
            TftTouchShield.drawRect(4, PLAYER_Y - 2, 2 * CARD_WIDTH + 12, CARD_HEIGHT + 4, HIGHLIGHT);
        }
        if (state != OUT && state != FOLDED && handNumber > 0) {
            drawCard(8, PLAYER_Y, cards[HOLE]);
            drawCard(8 + CARD_WIDTH + 4, PLAYER_Y, cards[HOLE + 1]);
        }
        int x = 2 * CARD_WIDTH + 22;
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(CHIP_TEXT, FELT);
        TftTouchShield.setCursor(x, PLAYER_Y + 2);
        TftTouchShield.print(seats[CHIPS]);
        if (dealer == 0 && handNumber > 0) {
            drawDealerButton(WIDTH - 12, PLAYER_Y + 8);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(x, PLAYER_Y + 24);
        drawActionText(seats, 0, FELT, reveal, cards, work);
        if (state != OUT && state != FOLDED && boardCount >= 3) {
            TftTouchShield.setTextColor(TftTouchShield.CYAN, FELT);
            TftTouchShield.setCursor(x, PLAYER_Y + 40);
            TftTouchShield.print(categoryName(scoreSeat(cards, work, 0, boardCount)));
        }
    }

    static void drawActionText(int[] seats, int seat, int background, boolean reveal, byte[] cards,
                                       int[] work) {
        int action = seats[ACTION + seat];
        int bet = seats[BET + seat];
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, background);
        if (action == WINNER) {
            TftTouchShield.setTextColor(TftTouchShield.GREEN, background);
            TftTouchShield.print("WINS");
        } else if (seats[STATE + seat] == ALL_IN) {
            TftTouchShield.setTextColor(TftTouchShield.ORANGE, background);
            TftTouchShield.print("ALL IN ");
            TftTouchShield.print(seats[TOTAL + seat]);
        } else if (action == FOLD) {
            TftTouchShield.setTextColor(TftTouchShield.GRAY, background);
            TftTouchShield.print("Fold");
        } else if (action == CHECK) {
            TftTouchShield.print("Check");
        } else if (action == CALL) {
            TftTouchShield.print("Call ");
            TftTouchShield.print(bet);
        } else if (action == RAISE) {
            TftTouchShield.setTextColor(TftTouchShield.ORANGE, background);
            TftTouchShield.print("Raise ");
            TftTouchShield.print(bet);
        } else if (action == SMALL_BLIND) {
            TftTouchShield.print("SB ");
            TftTouchShield.print(bet);
        } else if (action == BIG_BLIND) {
            TftTouchShield.print("BB ");
            TftTouchShield.print(bet);
        }
    }

    static void drawDealerButton(int cx, int cy) {
        TftTouchShield.fillCircle(cx, cy, 6, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, TftTouchShield.WHITE);
        TftTouchShield.setCursor(cx - 2, cy - 3);
        TftTouchShield.print("D");
    }

    static void showWinner(int seat, int amount, int score) {
        if (seat < 0) {
            return;
        }
        TftTouchShield.fillRect(0, MESSAGE_Y - 2, WIDTH, 14, FELT);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GREEN, FELT);
        TftTouchShield.setCursor(8, MESSAGE_Y);
        TftTouchShield.print(seatName(seat));
        if (seat == 0) {
            TftTouchShield.print(" win ");
        } else {
            TftTouchShield.print(" wins ");
        }
        TftTouchShield.print(amount);
        if (score >= 0) {
            TftTouchShield.print(" - ");
            TftTouchShield.print(categoryName(score));
        }
    }

    static void showMessage(String text, int color) {
        TftTouchShield.fillRect(0, MESSAGE_Y - 2, WIDTH, 14, FELT);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(color, FELT);
        TftTouchShield.setCursor(8, MESSAGE_Y);
        TftTouchShield.print(text);
    }

    static String seatName(int seat) {
        if (seat == 0) {
            return "You";
        }
        if (seat == 1) {
            return "Ann";
        }
        if (seat == 2) {
            return "Bob";
        }
        return "Cal";
    }

    static void clearControls() {
        TftTouchShield.fillRect(0, ROW1_Y - 2, WIDTH, 320 - ROW1_Y + 2, HEADER_BACKGROUND);
    }

    static void drawDealButtons(boolean dealEnabled) {
        clearControls();
        drawButton(1, ROW2_Y, "DEAL", dealEnabled);
    }

    static void drawActionButtons(int[] seats, int toCall, boolean canRaise) {
        clearControls();
        drawSmallButton(4, 40, "-", canRaise);
        TftTouchShield.fillRect(48, ROW1_Y, 80, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(CHIP_TEXT, TftTouchShield.BLACK);
        TftTouchShield.setCursor(52, ROW1_Y + 7);
        if (canRaise) {
            TftTouchShield.print(raiseTo);
        }
        drawSmallButton(132, 40, "+", canRaise);
        drawSmallButton(176, 60, "ALL", canRaise);
        drawButton(0, ROW2_Y, "FOLD", toCall > 0);
        if (toCall == 0) {
            drawButton(1, ROW2_Y, "CHECK", true);
        } else {
            drawCallButton(Math.min(toCall, seats[CHIPS]));
        }
        if (currentBet == 0) {
            drawButton(2, ROW2_Y, "BET", canRaise);
        } else {
            drawButton(2, ROW2_Y, "RAISE", canRaise);
        }
    }

    static void drawCallButton(int amount) {
        int x = 4 + 78;
        TftTouchShield.fillRect(x, ROW2_Y, 72, BUTTON_HEIGHT, BUTTON);
        TftTouchShield.drawRect(x, ROW2_Y, 72, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, BUTTON);
        TftTouchShield.setCursor(x + 6, ROW2_Y + 10);
        TftTouchShield.print("CALL ");
        TftTouchShield.print(amount);
    }

    static void drawButton(int index, int y, String label, boolean enabled) {
        int x = 4 + index * 78;
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        TftTouchShield.fillRect(x, y, 72, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(x, y, 72, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(x + (72 - label.length() * 12) / 2, y + 7);
        TftTouchShield.print(label);
    }

    static void drawSmallButton(int x, int width, String label, boolean enabled) {
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        TftTouchShield.fillRect(x, ROW1_Y, width, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(x, ROW1_Y, width, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(x + (width - label.length() * 12) / 2, ROW1_Y + 7);
        TftTouchShield.print(label);
    }
}
