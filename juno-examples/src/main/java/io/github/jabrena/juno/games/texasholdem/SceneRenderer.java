package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

import static io.github.jabrena.juno.games.texasholdem.CardsRenderer.drawCard;
import static io.github.jabrena.juno.games.texasholdem.CardsRenderer.drawSmallBack;
import static io.github.jabrena.juno.games.texasholdem.CardsRenderer.drawSmallCard;
import static io.github.jabrena.juno.games.texasholdem.HandEvaluator.categoryName;
import static io.github.jabrena.juno.games.texasholdem.HandEvaluator.scoreSeat;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.ACTION;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.ALL_IN;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.BET;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.BIG_BLIND;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.BOARD;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.BOARD_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.BUTTON;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.BUTTON_DISABLED;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.BUTTON_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CALL;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CARD_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CARD_WIDTH;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CHECK;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CHIPS;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.CHIP_TEXT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.FELT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.FOLD;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.FOLDED;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.HEADER_BACKGROUND;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.HEADER_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.HIGHLIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.HOLE;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.MESSAGE_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.NEW_X;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.OUT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.PLAYER_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.POT_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.RAISE;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.ROW1_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.ROW2_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SEATS;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SEAT_BACKGROUND;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SEAT_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SEAT_WIDTH;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SEAT_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SMALL_BLIND;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.SMALL_WIDTH;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.STATE;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.TOTAL;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.WIDTH;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.WINNER;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.boardCount;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.currentBet;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.dealer;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.handNumber;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.raiseTo;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.smallBlind;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.toAct;

final class SceneRenderer {
    private SceneRenderer() {
    }

    static void drawTable(TftTouchShield tft, byte[] cards, int[] seats, int[] work, boolean reveal) {
        tft.fillRect(0, HEADER_HEIGHT, WIDTH, ROW1_Y - HEADER_HEIGHT - 2, FELT);
        drawHeader(tft);
        for (int seat = 0; seat < SEATS; seat++) {
            drawSeat(tft, cards, seats, work, seat, reveal);
        }
        for (int i = 0; i < 5; i++) {
            int x = 8 + i * 46;
            if (i < boardCount) {
                drawCard(tft, x, BOARD_Y, cards[BOARD + i]);
            } else {
                tft.drawRect(x, BOARD_Y, CARD_WIDTH, CARD_HEIGHT, SEAT_BACKGROUND);
            }
        }
        drawPot(tft, seats);
    }

    static void drawHeader(TftTouchShield tft) {
        tft.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        tft.setCursor(4, 5);
        tft.print("HAND ");
        tft.print(handNumber);
        tft.print("   BLINDS ");
        tft.print(smallBlind);
        tft.print("/");
        tft.print(smallBlind * 2);
        tft.fillRect(NEW_X, 1, WIDTH - NEW_X - 2, HEADER_HEIGHT - 2, BUTTON);
        tft.setTextColor(TftTouchShield.BLACK, BUTTON);
        tft.setCursor(NEW_X + 12, 5);
        tft.print("NEW");
    }

    static void drawPot(TftTouchShield tft, int[] seats) {
        int pot = 0;
        for (int seat = 0; seat < SEATS; seat++) {
            pot = pot + seats[TOTAL + seat];
        }
        tft.fillRect(0, POT_Y, WIDTH, 18, FELT);
        if (pot == 0) {
            return;
        }
        tft.setTextSize(2);
        tft.setTextColor(CHIP_TEXT, FELT);
        int digits = String.valueOf(pot).length();
        tft.setCursor((WIDTH - (4 + digits) * 12) / 2, POT_Y + 1);
        tft.print("Pot ");
        tft.print(pot);
    }

    static void drawSeat(TftTouchShield tft, byte[] cards, int[] seats, int[] work, int seat, boolean reveal) {
        int state = seats[STATE + seat];
        if (seat == 0) {
            drawPlayer(tft, cards, seats, work, reveal);
            return;
        }
        int x = 2 + (seat - 1) * (SEAT_WIDTH + 1);
        int border = SEAT_BACKGROUND;
        if (toAct == seat) {
            border = HIGHLIGHT;
        } else if (seats[ACTION + seat] == WINNER) {
            border = TftTouchShield.GREEN;
        }
        tft.fillRect(x, SEAT_Y, SEAT_WIDTH, SEAT_HEIGHT, SEAT_BACKGROUND);
        tft.drawRect(x, SEAT_Y, SEAT_WIDTH, SEAT_HEIGHT, border);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, SEAT_BACKGROUND);
        tft.setCursor(x + 4, SEAT_Y + 4);
        tft.print(seatName(seat));
        if (seat == dealer && state != OUT && handNumber > 0) {
            drawDealerButton(tft, x + SEAT_WIDTH - 10, SEAT_Y + 7);
        }
        tft.setTextColor(CHIP_TEXT, SEAT_BACKGROUND);
        tft.setCursor(x + 4, SEAT_Y + 14);
        tft.print(seats[CHIPS + seat]);
        if (state == OUT) {
            tft.setTextColor(TftTouchShield.GRAY, SEAT_BACKGROUND);
            tft.setCursor(x + 4, SEAT_Y + 36);
            tft.print("OUT");
            return;
        }
        if (state != FOLDED) {
            for (int i = 0; i < 2; i++) {
                int cardX = x + 14 + i * (SMALL_WIDTH + 4);
                if (reveal) {
                    drawSmallCard(tft, cardX, SEAT_Y + 25, cards[HOLE + seat * 2 + i]);
                } else {
                    drawSmallBack(tft, cardX, SEAT_Y + 25);
                }
            }
        }
        tft.setCursor(x + 4, SEAT_Y + 62);
        drawActionText(tft, seats, seat, SEAT_BACKGROUND, reveal, cards, work);
    }

    static void drawPlayer(TftTouchShield tft, byte[] cards, int[] seats, int[] work, boolean reveal) {
        tft.fillRect(0, PLAYER_Y - 2, WIDTH, ROW1_Y - PLAYER_Y, FELT);
        int state = seats[STATE];
        if (toAct == 0) {
            tft.drawRect(4, PLAYER_Y - 2, 2 * CARD_WIDTH + 12, CARD_HEIGHT + 4, HIGHLIGHT);
        }
        if (state != OUT && state != FOLDED && handNumber > 0) {
            drawCard(tft, 8, PLAYER_Y, cards[HOLE]);
            drawCard(tft, 8 + CARD_WIDTH + 4, PLAYER_Y, cards[HOLE + 1]);
        }
        int x = 2 * CARD_WIDTH + 22;
        tft.setTextSize(2);
        tft.setTextColor(CHIP_TEXT, FELT);
        tft.setCursor(x, PLAYER_Y + 2);
        tft.print(seats[CHIPS]);
        if (dealer == 0 && handNumber > 0) {
            drawDealerButton(tft, WIDTH - 12, PLAYER_Y + 8);
        }
        tft.setTextSize(1);
        tft.setCursor(x, PLAYER_Y + 24);
        drawActionText(tft, seats, 0, FELT, reveal, cards, work);
        if (state != OUT && state != FOLDED && boardCount >= 3) {
            tft.setTextColor(TftTouchShield.CYAN, FELT);
            tft.setCursor(x, PLAYER_Y + 40);
            tft.print(categoryName(scoreSeat(cards, work, 0, boardCount)));
        }
    }

    static void drawActionText(TftTouchShield tft, int[] seats, int seat, int background, boolean reveal, byte[] cards,
                                       int[] work) {
        int action = seats[ACTION + seat];
        int bet = seats[BET + seat];
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, background);
        if (action == WINNER) {
            tft.setTextColor(TftTouchShield.GREEN, background);
            tft.print("WINS");
        } else if (seats[STATE + seat] == ALL_IN) {
            tft.setTextColor(TftTouchShield.ORANGE, background);
            tft.print("ALL IN ");
            tft.print(seats[TOTAL + seat]);
        } else if (action == FOLD) {
            tft.setTextColor(TftTouchShield.GRAY, background);
            tft.print("Fold");
        } else if (action == CHECK) {
            tft.print("Check");
        } else if (action == CALL) {
            tft.print("Call ");
            tft.print(bet);
        } else if (action == RAISE) {
            tft.setTextColor(TftTouchShield.ORANGE, background);
            tft.print("Raise ");
            tft.print(bet);
        } else if (action == SMALL_BLIND) {
            tft.print("SB ");
            tft.print(bet);
        } else if (action == BIG_BLIND) {
            tft.print("BB ");
            tft.print(bet);
        }
    }

    static void drawDealerButton(TftTouchShield tft, int cx, int cy) {
        tft.fillCircle(cx, cy, 6, TftTouchShield.WHITE);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.BLACK, TftTouchShield.WHITE);
        tft.setCursor(cx - 2, cy - 3);
        tft.print("D");
    }

    static void showWinner(TftTouchShield tft, int seat, int amount, int score) {
        if (seat < 0) {
            return;
        }
        tft.fillRect(0, MESSAGE_Y - 2, WIDTH, 14, FELT);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.GREEN, FELT);
        tft.setCursor(8, MESSAGE_Y);
        tft.print(seatName(seat));
        if (seat == 0) {
            tft.print(" win ");
        } else {
            tft.print(" wins ");
        }
        tft.print(amount);
        if (score >= 0) {
            tft.print(" - ");
            tft.print(categoryName(score));
        }
    }

    static void showMessage(TftTouchShield tft, String text, int color) {
        tft.fillRect(0, MESSAGE_Y - 2, WIDTH, 14, FELT);
        tft.setTextSize(1);
        tft.setTextColor(color, FELT);
        tft.setCursor(8, MESSAGE_Y);
        tft.print(text);
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

    static void clearControls(TftTouchShield tft) {
        tft.fillRect(0, ROW1_Y - 2, WIDTH, 320 - ROW1_Y + 2, HEADER_BACKGROUND);
    }

    static void drawDealButtons(TftTouchShield tft, boolean dealEnabled) {
        clearControls(tft);
        drawButton(tft, 1, ROW2_Y, "DEAL", dealEnabled);
    }

    static void drawActionButtons(TftTouchShield tft, int[] seats, int toCall, boolean canRaise) {
        clearControls(tft);
        drawSmallButton(tft, 4, 40, "-", canRaise);
        tft.fillRect(48, ROW1_Y, 80, BUTTON_HEIGHT, TftTouchShield.BLACK);
        tft.setTextSize(2);
        tft.setTextColor(CHIP_TEXT, TftTouchShield.BLACK);
        tft.setCursor(52, ROW1_Y + 7);
        if (canRaise) {
            tft.print(raiseTo);
        }
        drawSmallButton(tft, 132, 40, "+", canRaise);
        drawSmallButton(tft, 176, 60, "ALL", canRaise);
        drawButton(tft, 0, ROW2_Y, "FOLD", toCall > 0);
        if (toCall == 0) {
            drawButton(tft, 1, ROW2_Y, "CHECK", true);
        } else {
            drawCallButton(tft, Math.min(toCall, seats[CHIPS]));
        }
        if (currentBet == 0) {
            drawButton(tft, 2, ROW2_Y, "BET", canRaise);
        } else {
            drawButton(tft, 2, ROW2_Y, "RAISE", canRaise);
        }
    }

    static void drawCallButton(TftTouchShield tft, int amount) {
        int x = 4 + 78;
        tft.fillRect(x, ROW2_Y, 72, BUTTON_HEIGHT, BUTTON);
        tft.drawRect(x, ROW2_Y, 72, BUTTON_HEIGHT, TftTouchShield.BLACK);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.BLACK, BUTTON);
        tft.setCursor(x + 6, ROW2_Y + 10);
        tft.print("CALL ");
        tft.print(amount);
    }

    static void drawButton(TftTouchShield tft, int index, int y, String label, boolean enabled) {
        int x = 4 + index * 78;
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        tft.fillRect(x, y, 72, BUTTON_HEIGHT, color);
        tft.drawRect(x, y, 72, BUTTON_HEIGHT, TftTouchShield.BLACK);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.BLACK, color);
        tft.setCursor(x + (72 - label.length() * 12) / 2, y + 7);
        tft.print(label);
    }

    static void drawSmallButton(TftTouchShield tft, int x, int width, String label, boolean enabled) {
        int color = BUTTON_DISABLED;
        if (enabled) {
            color = BUTTON;
        }
        tft.fillRect(x, ROW1_Y, width, BUTTON_HEIGHT, color);
        tft.drawRect(x, ROW1_Y, width, BUTTON_HEIGHT, TftTouchShield.BLACK);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.BLACK, color);
        tft.setCursor(x + (width - label.length() * 12) / 2, ROW1_Y + 7);
        tft.print(label);
    }
}
