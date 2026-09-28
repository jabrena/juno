package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Blackjack against the dealer on the ELEGOO 2.8" TFT touch screen shield.
 *
 * <p>Set your bet with {@code -} and {@code +} (steps of 5), then {@code DEAL}. During a hand, tap
 * {@code HIT}, {@code STAND} or {@code DBL} (double down: double the bet, take exactly one more
 * card; only on your first two cards). The dealer stands on all 17s, a blackjack pays 3:2, and a
 * tie is a push. Splitting and insurance are not offered. You start with 100 chips; when you run
 * out, the next deal refills the bank.
 *
 * <p>The game uses a single 52-card deck, shuffled with Fisher-Yates and reshuffled when fewer than
 * 15 cards remain. Cards are numbered 0-51: {@code card % 13} is the rank (0 = ace ... 12 = king)
 * and {@code card / 13} the suit (spades, hearts, diamonds, clubs). The suit symbols are 9x9
 * bitmaps, since the display font has no ♠♥♦♣ glyphs.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Blackjack {
    private static final int START_BANK = 100;
    private static final int MIN_BET = 5;
    private static final int BET_STEP = 5;
    private static final int RESHUFFLE_BELOW = 15;
    private static final int MAX_CARDS = 12;

    private static final int SPADES = 0;
    private static final int HEARTS = 1;
    private static final int DIAMONDS = 2;

    // Phases.
    private static final int BETTING = 0;
    private static final int PLAYING = 1;

    // Layout (portrait, 240x320).
    private static final int HEADER_HEIGHT = 32;
    private static final int CARD_WIDTH = 40;
    private static final int CARD_HEIGHT = 56;
    private static final int HAND_X = 8;
    private static final int DEALER_LABEL_Y = 40;
    private static final int DEALER_CARDS_Y = 58;
    private static final int MESSAGE_Y = 126;
    private static final int PLAYER_LABEL_Y = 156;
    private static final int PLAYER_CARDS_Y = 174;
    private static final int BUTTON_Y = 262;
    private static final int BUTTON_WIDTH = 72;
    private static final int BUTTON_HEIGHT = 44;
    private static final int BUTTON_SPACING = 76;

    private static final int FELT = 0x0366;
    private static final int CARD_BACK = 0x1152;
    private static final int BUTTON = 0xCD05;
    private static final int BUTTON_DISABLED = 0x39E7;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static int phase;
    private static int bank;
    private static int bet;
    private static int handBet;
    private static int deckPosition;
    private static int playerCount;
    private static int dealerCount;
    private static boolean holeHidden;
    private static boolean seeded;

    private Blackjack() {
    }

    public static void main(String[] args) {
        byte[] deck = new byte[52];
        byte[] player = new byte[MAX_CARDS];
        byte[] dealer = new byte[MAX_CARDS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(FELT);
        bank = START_BANK;
        bet = 10;
        deckPosition = 52;
        phase = BETTING;
        drawHeader();
        showMessage("Place your bet", TftTouchShield.WHITE);
        drawButtons(player);

        while (true) {
            int button = waitForButton();
            if (phase == BETTING) {
                if (button == 0) {
                    bet = Math.max(MIN_BET, bet - BET_STEP);
                    drawHeader();
                    drawButtons(player);
                } else if (button == 1) {
                    bet = Math.min(Math.max(MIN_BET, bank), bet + BET_STEP);
                    drawHeader();
                    drawButtons(player);
                } else {
                    deal(deck, player, dealer);
                }
            } else if (button == 0) {
                hit(deck, player, dealer);
            } else if (button == 1) {
                dealerTurn(deck, player, dealer);
            } else if (canDouble()) {
                doubleDown(deck, player, dealer);
            }
        }
    }

    // ---- Game flow ----

    private static void deal(byte[] deck, byte[] player, byte[] dealer) {
        if (!seeded) {
            Random.seed(Clock.micros());
            seeded = true;
        }
        if (bank < MIN_BET) {
            bank = START_BANK;
        }
        bet = Math.min(bet, bank);
        if (deckPosition > 52 - RESHUFFLE_BELOW) {
            shuffle(deck);
            showMessage("Shuffling...", TftTouchShield.YELLOW);
            Delay.millis(600);
        }
        handBet = bet;
        bank = bank - handBet;
        drawHeader();
        showMessage("", TftTouchShield.WHITE);

        playerCount = 0;
        dealerCount = 0;
        holeHidden = true;
        clearHands();
        player[playerCount] = draw(deck);
        playerCount = playerCount + 1;
        drawHand(player, playerCount, false, PLAYER_LABEL_Y, PLAYER_CARDS_Y, "You");
        Delay.millis(250);
        dealer[dealerCount] = draw(deck);
        dealerCount = dealerCount + 1;
        drawHand(dealer, dealerCount, false, DEALER_LABEL_Y, DEALER_CARDS_Y, "Dealer");
        Delay.millis(250);
        player[playerCount] = draw(deck);
        playerCount = playerCount + 1;
        drawHand(player, playerCount, false, PLAYER_LABEL_Y, PLAYER_CARDS_Y, "You");
        Delay.millis(250);
        dealer[dealerCount] = draw(deck);
        dealerCount = dealerCount + 1;
        drawHand(dealer, dealerCount, true, DEALER_LABEL_Y, DEALER_CARDS_Y, "Dealer");

        boolean playerBlackjack = handValue(player, playerCount) == 21;
        boolean dealerBlackjack = handValue(dealer, dealerCount) == 21;
        if (playerBlackjack || dealerBlackjack) {
            revealHole(dealer);
            if (playerBlackjack && dealerBlackjack) {
                settle(handBet, "Push", TftTouchShield.WHITE, player);
            } else if (playerBlackjack) {
                settle(handBet + handBet * 3 / 2, "Blackjack!", TftTouchShield.YELLOW, player);
            } else {
                settle(0, "Dealer blackjack", TftTouchShield.RED, player);
            }
            return;
        }
        phase = PLAYING;
        drawButtons(player);
    }

    private static void hit(byte[] deck, byte[] player, byte[] dealer) {
        player[playerCount] = draw(deck);
        playerCount = playerCount + 1;
        drawHand(player, playerCount, false, PLAYER_LABEL_Y, PLAYER_CARDS_Y, "You");
        int value = handValue(player, playerCount);
        if (value > 21) {
            revealHole(dealer);
            settle(0, "Bust!", TftTouchShield.RED, player);
        } else if (value == 21 || playerCount == MAX_CARDS) {
            dealerTurn(deck, player, dealer);
        } else {
            drawButtons(player);
        }
    }

    private static boolean canDouble() {
        return playerCount == 2 && bank >= handBet;
    }

    private static void doubleDown(byte[] deck, byte[] player, byte[] dealer) {
        bank = bank - handBet;
        handBet = handBet * 2;
        drawHeader();
        player[playerCount] = draw(deck);
        playerCount = playerCount + 1;
        drawHand(player, playerCount, false, PLAYER_LABEL_Y, PLAYER_CARDS_Y, "You");
        if (handValue(player, playerCount) > 21) {
            revealHole(dealer);
            settle(0, "Bust!", TftTouchShield.RED, player);
            return;
        }
        dealerTurn(deck, player, dealer);
    }

    // Reveals the hole card, draws to 17 (standing on soft 17), then compares hands.
    private static void dealerTurn(byte[] deck, byte[] player, byte[] dealer) {
        revealHole(dealer);
        while (handValue(dealer, dealerCount) < 17 && dealerCount < MAX_CARDS) {
            Delay.millis(600);
            dealer[dealerCount] = draw(deck);
            dealerCount = dealerCount + 1;
            drawHand(dealer, dealerCount, false, DEALER_LABEL_Y, DEALER_CARDS_Y, "Dealer");
        }
        int mine = handValue(player, playerCount);
        int theirs = handValue(dealer, dealerCount);
        if (theirs > 21) {
            settle(handBet * 2, "Dealer busts!", TftTouchShield.GREEN, player);
        } else if (mine > theirs) {
            settle(handBet * 2, "You win!", TftTouchShield.GREEN, player);
        } else if (mine == theirs) {
            settle(handBet, "Push", TftTouchShield.WHITE, player);
        } else {
            settle(0, "Dealer wins", TftTouchShield.RED, player);
        }
    }

    private static void revealHole(byte[] dealer) {
        if (holeHidden) {
            holeHidden = false;
            drawHand(dealer, dealerCount, false, DEALER_LABEL_Y, DEALER_CARDS_Y, "Dealer");
        }
    }

    /** Pays {@code payout} (stake included) back to the bank and returns to betting. */
    private static void settle(int payout, String message, int color, byte[] player) {
        bank = bank + payout;
        phase = BETTING;
        if (bank < MIN_BET) {
            showMessage("Out of chips!", TftTouchShield.RED);
        } else {
            showMessage(message, color);
        }
        bet = Math.max(MIN_BET, Math.min(bet, bank));
        drawHeader();
        drawButtons(player);
    }

    // ---- Cards ----

    private static void shuffle(byte[] deck) {
        for (int i = 0; i < 52; i++) {
            deck[i] = (byte) i;
        }
        for (int i = 51; i > 0; i--) {
            int j = Random.nextInt(i + 1);
            byte swap = deck[i];
            deck[i] = deck[j];
            deck[j] = swap;
        }
        deckPosition = 0;
    }

    private static byte draw(byte[] deck) {
        if (deckPosition == 52) {
            shuffle(deck);
        }
        byte card = deck[deckPosition];
        deckPosition = deckPosition + 1;
        return card;
    }

    /** Best total: aces count 11 when that does not bust the hand, otherwise 1. */
    private static int handValue(byte[] cards, int count) {
        int total = 0;
        boolean ace = false;
        for (int i = 0; i < count; i++) {
            int rank = cards[i] % 13;
            if (rank == 0) {
                ace = true;
                total = total + 1;
            } else if (rank >= 9) {
                total = total + 10;
            } else {
                total = total + rank + 1;
            }
        }
        if (ace && total + 10 <= 21) {
            total = total + 10;
        }
        return total;
    }

    // ---- Input ----

    /** Waits for a tap on one of the three buttons and returns its index (0-2). */
    private static int waitForButton() {
        while (true) {
            if (TftTouchShield.readTouch()) {
                int x = TftTouchShield.touchX();
                int y = TftTouchShield.touchY();
                waitForRelease();
                if (y >= BUTTON_Y && y < BUTTON_Y + BUTTON_HEIGHT && x >= HAND_X) {
                    int button = (x - HAND_X) / BUTTON_SPACING;
                    if (button <= 2) {
                        return button;
                    }
                }
            }
            Delay.millis(10);
        }
    }

    private static void waitForRelease() {
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }

    // ---- Drawing ----

    private static void drawHeader() {
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

    private static void showMessage(String text, int color) {
        TftTouchShield.fillRect(0, MESSAGE_Y, TftTouchShield.width(), 24, FELT);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, FELT);
        TftTouchShield.setCursor((TftTouchShield.width() - text.length() * 12) / 2, MESSAGE_Y + 4);
        TftTouchShield.print(text);
    }

    private static void drawButtons(byte[] player) {
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

    private static void drawButton(int index, String label, boolean enabled) {
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

    private static void clearHands() {
        TftTouchShield.fillRect(0, DEALER_LABEL_Y, TftTouchShield.width(), MESSAGE_Y - DEALER_LABEL_Y, FELT);
        TftTouchShield.fillRect(0, PLAYER_LABEL_Y, TftTouchShield.width(), BUTTON_Y - PLAYER_LABEL_Y - 4, FELT);
    }

    /** Redraws a hand's label (with its value) and its cards, overlapping them when needed. */
    private static void drawHand(byte[] cards, int count, boolean hideSecond, int labelY, int cardsY, String name) {
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

    private static void drawCard(int x, int y, int card) {
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

    private static void drawCardBack(int x, int y) {
        TftTouchShield.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.fillRect(x + 3, y + 3, CARD_WIDTH - 6, CARD_HEIGHT - 6, CARD_BACK);
        for (int i = 0; i < 4; i++) {
            TftTouchShield.drawRect(x + 6 + i * 4, y + 6 + i * 4, CARD_WIDTH - 12 - i * 8, CARD_HEIGHT - 12 - i * 8,
                    TftTouchShield.WHITE);
        }
    }

    // Streams a 9x9 suit bitmap scaled by {@code scale}, on the card's white background.
    private static void drawSuit(int x, int y, int suit, int scale, int ink) {
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

    private static String rankName(int rank) {
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

    // 9x9 suit symbols, bit n = column n.
    private static int suitRow(int suit, int row) {
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

    private static int spadeRow(int row) {
        if (row == 0 || row == 7) {
            return 0x010;
        }
        if (row == 1 || row == 8) {
            return 0x038;
        }
        if (row == 2) {
            return 0x07C;
        }
        if (row == 3) {
            return 0x0FE;
        }
        if (row == 4 || row == 5) {
            return 0x1FF;
        }
        return 0x0D6;
    }

    private static int heartRow(int row) {
        if (row == 0) {
            return 0x0C6;
        }
        if (row == 1) {
            return 0x1EF;
        }
        if (row == 2 || row == 3) {
            return 0x1FF;
        }
        if (row == 4) {
            return 0x0FE;
        }
        if (row == 5) {
            return 0x07C;
        }
        if (row == 6) {
            return 0x038;
        }
        if (row == 7) {
            return 0x010;
        }
        return 0;
    }

    private static int diamondRow(int row) {
        if (row == 0 || row == 8) {
            return 0x010;
        }
        if (row == 1 || row == 7) {
            return 0x038;
        }
        if (row == 2 || row == 6) {
            return 0x07C;
        }
        if (row == 3 || row == 5) {
            return 0x0FE;
        }
        return 0x1FF;
    }

    private static int clubRow(int row) {
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
