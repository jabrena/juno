package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Klondike solitaire (draw one) on the ELEGOO 2.8" TFT touch screen shield.
 *
 * <p>The top row holds the stock, the waste and the four foundations (one per suit, built up from
 * the ace); below are the seven tableau columns, built down in alternating colors. Tap a card to
 * pick it up (a tableau card picks up everything on top of it too), then tap where it should go.
 * Tapping a picked-up card again sends it to its foundation when it can go there. Tap the stock to
 * turn a card over, and the empty stock to turn the waste back over. Only a king may fill an empty
 * column. Once every card is face up and the stock and waste are empty, the rest plays itself.
 * {@code NEW} deals again.
 *
 * <p>Scoring follows the usual Windows rules: 5 points for moving a card from the waste to the
 * tableau and for turning over a tableau card, 10 for each card played to a foundation, minus 15
 * for taking one back from a foundation, and minus 100 (down to 0) for each pass through the waste.
 *
 * <p>Cards are numbered 0-51 as in {@link Blackjack}: {@code card % 13} is the rank (0 = ace ...
 * 12 = king) and {@code card / 13} the suit (spades, hearts, diamonds, clubs). The thirteen piles
 * share one {@code byte[]} with {@value #CAPACITY} slots each, bottom card first; a second array
 * holds each pile's size and, for the tableau, how many of its bottom cards are face down.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Solitaire {
    // Piles: 0-6 tableau, 7-10 foundations (one per suit), 11 stock, 12 waste.
    private static final int TABLEAU = 0;
    private static final int FOUNDATION = 7;
    private static final int STOCK = 11;
    private static final int WASTE = 12;
    private static final int PILES = 13;
    private static final int CAPACITY = 24;
    /** {@code meta[HIDDEN + column]} is how many of a tableau column's cards are face down. */
    private static final int HIDDEN = PILES;

    private static final int HEARTS = 1;
    private static final int DIAMONDS = 2;
    private static final int KING = 12;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEIGHT = 320;
    private static final int HEADER_HEIGHT = 22;
    private static final int NEW_X = 184;
    private static final int COLUMN_WIDTH = 34;
    private static final int CARD_WIDTH = 32;
    private static final int CARD_HEIGHT = 44;
    private static final int TOP_Y = 26;
    private static final int TABLEAU_Y = 78;
    private static final int HIDDEN_STEP = 4;
    private static final int FACE_UP_STEP = 13;

    private static final int FELT = 0x0366;
    private static final int SLOT = 0x0244;
    private static final int CARD_BACK = 0x1152;
    private static final int SELECTED = TftTouchShield.YELLOW;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON = 0xCD05;

    private static int score;
    private static int moves;
    private static int selectedPile;
    private static int selectedIndex;
    private static int tapX;
    private static int tapY;

    private Solitaire() {
    }

    public static void main(String[] args) {
        byte[] cards = new byte[PILES * CAPACITY];
        int[] meta = new int[PILES + 7];
        byte[] deck = new byte[52];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(FELT);
        showBanner("SOLITAIRE", TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, FELT);
        TftTouchShield.setCursor((WIDTH - 11 * 12) / 2, 180);
        TftTouchShield.print("Tap to deal");
        waitForTap();
        Random.seed(Clock.micros());
        deal(cards, meta, deck);

        while (true) {
            waitForTap();
            if (tapY < HEADER_HEIGHT) {
                if (tapX >= NEW_X) {
                    deal(cards, meta, deck);
                }
            } else {
                tapTable(cards, meta);
            }
        }
    }

    // ---- Game flow ----

    private static void deal(byte[] cards, int[] meta, byte[] deck) {
        for (int i = 0; i < 52; i++) {
            deck[i] = (byte) i;
        }
        for (int i = 51; i > 0; i--) {
            int j = Random.nextInt(i + 1);
            byte swap = deck[i];
            deck[i] = deck[j];
            deck[j] = swap;
        }
        for (int pile = 0; pile < PILES + 7; pile++) {
            meta[pile] = 0;
        }
        int next = 0;
        for (int column = 0; column < 7; column++) {
            for (int i = 0; i <= column; i++) {
                push(cards, meta, TABLEAU + column, deck[next]);
                next = next + 1;
            }
            meta[HIDDEN + column] = column;
        }
        while (next < 52) {
            push(cards, meta, STOCK, deck[next]);
            next = next + 1;
        }
        score = 0;
        moves = 0;
        selectedPile = -1;
        TftTouchShield.fillScreen(FELT);
        drawHeader();
        for (int pile = 0; pile < PILES; pile++) {
            drawPile(cards, meta, pile);
        }
    }

    /** Works out which pile and card a tap hit and picks up, moves, or drops accordingly. */
    private static void tapTable(byte[] cards, int[] meta) {
        int column = Math.min(6, tapX / COLUMN_WIDTH);
        int pile = -1;
        int index = -1;
        if (tapY < TOP_Y + CARD_HEIGHT + 4) {
            if (column == 0) {
                clearSelection(cards, meta);
                turnStock(cards, meta);
                return;
            }
            if (column == 1) {
                pile = WASTE;
            } else if (column >= 3) {
                pile = FOUNDATION + column - 3;
            }
            if (pile >= 0) {
                index = meta[pile] - 1;
            }
        } else if (tapY >= TABLEAU_Y) {
            pile = TABLEAU + column;
            index = indexAt(meta, column, tapY);
        }

        if (selectedPile < 0) {
            select(cards, meta, pile, index);
            return;
        }
        if (pile == selectedPile) {
            if (index == selectedIndex || pile >= FOUNDATION) {
                // Second tap on the same card: send it home if it can go.
                int from = selectedPile;
                int at = selectedIndex;
                clearSelection(cards, meta);
                if (at == meta[from] - 1 && from < FOUNDATION) {
                    int card = cards[from * CAPACITY + at];
                    if (move(cards, meta, from, at, FOUNDATION + card / 13)) {
                        checkEnd(cards, meta);
                    }
                }
            } else {
                select(cards, meta, pile, index);
            }
            return;
        }
        if (pile >= 0 && move(cards, meta, selectedPile, selectedIndex, pile)) {
            checkEnd(cards, meta);
            return;
        }
        select(cards, meta, pile, index);
    }

    /** Picks up {@code index} of {@code pile} if that is a face-up card, otherwise drops the selection. */
    private static void select(byte[] cards, int[] meta, int pile, int index) {
        clearSelection(cards, meta);
        if (pile < 0 || index < 0 || index >= meta[pile]) {
            return;
        }
        if (pile < FOUNDATION && index < meta[HIDDEN + pile]) {
            return;
        }
        selectedPile = pile;
        selectedIndex = index;
        drawPile(cards, meta, pile);
    }

    private static void clearSelection(byte[] cards, int[] meta) {
        if (selectedPile >= 0) {
            int pile = selectedPile;
            selectedPile = -1;
            drawPile(cards, meta, pile);
        }
    }

    private static void turnStock(byte[] cards, int[] meta) {
        if (meta[STOCK] > 0) {
            push(cards, meta, WASTE, pop(cards, meta, STOCK));
        } else if (meta[WASTE] > 0) {
            while (meta[WASTE] > 0) {
                push(cards, meta, STOCK, pop(cards, meta, WASTE));
            }
            score = Math.max(0, score - 100);
        } else {
            return;
        }
        moves = moves + 1;
        drawPile(cards, meta, STOCK);
        drawPile(cards, meta, WASTE);
        drawHeader();
    }

    /**
     * Moves the cards from {@code index} up of pile {@code from} onto pile {@code to} when the rules
     * allow it, returning whether it did.
     */
    private static boolean move(byte[] cards, int[] meta, int from, int index, int to) {
        if (!canMove(cards, meta, from, index, to)) {
            return false;
        }
        int count = meta[from] - index;
        for (int i = 0; i < count; i++) {
            push(cards, meta, to, cards[from * CAPACITY + index + i]);
        }
        meta[from] = index;
        selectedPile = -1;
        moves = moves + 1;
        if (to >= FOUNDATION) {
            score = score + 10;
        } else if (from == WASTE) {
            score = score + 5;
        } else if (from >= FOUNDATION) {
            score = Math.max(0, score - 15);
        }
        if (from < FOUNDATION && meta[from] > 0 && meta[HIDDEN + from] == meta[from]) {
            meta[HIDDEN + from] = meta[from] - 1;
            score = score + 5;
        }
        drawPile(cards, meta, from);
        drawPile(cards, meta, to);
        drawHeader();
        return true;
    }

    /** After a move: plays the remaining cards home once nothing is hidden, and announces a win. */
    private static void checkEnd(byte[] cards, int[] meta) {
        if (canFinish(meta)) {
            finish(cards, meta);
        }
        if (won(meta)) {
            showBanner("You win!", TftTouchShield.YELLOW);
        }
    }

    private static boolean canMove(byte[] cards, int[] meta, int from, int index, int to) {
        if (from == to || index < 0 || index >= meta[from] || to == STOCK || to == WASTE) {
            return false;
        }
        int card = cards[from * CAPACITY + index];
        int rank = card % 13;
        if (to >= FOUNDATION) {
            return index == meta[from] - 1 && to == FOUNDATION + card / 13 && rank == meta[to];
        }
        if (meta[to] == 0) {
            return rank == KING;
        }
        int top = cards[to * CAPACITY + meta[to] - 1];
        return top % 13 == rank + 1 && isRed(top) != isRed(card);
    }

    private static boolean won(int[] meta) {
        for (int suit = 0; suit < 4; suit++) {
            if (meta[FOUNDATION + suit] != 13) {
                return false;
            }
        }
        return true;
    }

    /** True once nothing is hidden anymore: every remaining card can then simply be played home. */
    private static boolean canFinish(int[] meta) {
        if (meta[STOCK] > 0 || meta[WASTE] > 0) {
            return false;
        }
        for (int column = 0; column < 7; column++) {
            if (meta[HIDDEN + column] > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Plays every tableau card home, one at a time. With all cards face up in descending
     * alternating runs, some column's top card is always playable, so each pass moves one.
     */
    private static void finish(byte[] cards, int[] meta) {
        boolean moved = true;
        while (moved && !won(meta)) {
            moved = false;
            for (int column = 0; column < 7 && !moved; column++) {
                int count = meta[TABLEAU + column];
                if (count > 0) {
                    int card = cards[(TABLEAU + column) * CAPACITY + count - 1];
                    if (canMove(cards, meta, TABLEAU + column, count - 1, FOUNDATION + card / 13)) {
                        Delay.millis(80);
                        moved = move(cards, meta, TABLEAU + column, count - 1, FOUNDATION + card / 13);
                    }
                }
            }
        }
    }

    // ---- Piles ----

    private static void push(byte[] cards, int[] meta, int pile, int card) {
        cards[pile * CAPACITY + meta[pile]] = (byte) card;
        meta[pile] = meta[pile] + 1;
    }

    private static int pop(byte[] cards, int[] meta, int pile) {
        meta[pile] = meta[pile] - 1;
        return cards[pile * CAPACITY + meta[pile]];
    }

    private static boolean isRed(int card) {
        int suit = card / 13;
        return suit == HEARTS || suit == DIAMONDS;
    }

    /** Vertical gap between face-up cards, squeezed so that a long column still fits on screen. */
    private static int faceUpStep(int[] meta, int column) {
        int faceUp = meta[TABLEAU + column] - meta[HIDDEN + column];
        if (faceUp <= 1) {
            return FACE_UP_STEP;
        }
        int room = HEIGHT - 2 - TABLEAU_Y - CARD_HEIGHT - meta[HIDDEN + column] * HIDDEN_STEP;
        return Math.min(FACE_UP_STEP, room / (faceUp - 1));
    }

    private static int cardY(int[] meta, int column, int index, int step) {
        int hidden = meta[HIDDEN + column];
        if (index <= hidden) {
            return TABLEAU_Y + index * HIDDEN_STEP;
        }
        return TABLEAU_Y + hidden * HIDDEN_STEP + (index - hidden) * step;
    }

    /** The card of a tableau column under {@code y}; below the column counts as its top card. */
    private static int indexAt(int[] meta, int column, int y) {
        int count = meta[TABLEAU + column];
        int step = faceUpStep(meta, column);
        int index = count - 1;
        while (index > 0 && cardY(meta, column, index, step) > y) {
            index = index - 1;
        }
        return index;
    }

    // ---- Input ----

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        tapX = TftTouchShield.touchX();
        tapY = TftTouchShield.touchY();
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
        TftTouchShield.fillRect(0, 0, NEW_X - 4, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 7);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(score);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(92, 7);
        TftTouchShield.print("MOVES ");
        TftTouchShield.print(moves);
        TftTouchShield.fillRect(NEW_X - 4, 0, WIDTH - NEW_X + 4, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.fillRect(NEW_X, 1, WIDTH - NEW_X - 2, HEADER_HEIGHT - 2, BUTTON);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, BUTTON);
        TftTouchShield.setCursor(NEW_X + 9, 4);
        TftTouchShield.print("NEW");
    }

    private static void drawPile(byte[] cards, int[] meta, int pile) {
        if (pile < FOUNDATION) {
            drawColumn(cards, meta, pile - TABLEAU);
            return;
        }
        int x = 1;
        if (pile == WASTE) {
            x = 1 + COLUMN_WIDTH;
        } else if (pile < STOCK) {
            x = 1 + (pile - FOUNDATION + 3) * COLUMN_WIDTH;
        }
        TftTouchShield.fillRect(x, TOP_Y, CARD_WIDTH, CARD_HEIGHT, FELT);
        int count = meta[pile];
        if (count == 0) {
            TftTouchShield.drawRect(x, TOP_Y, CARD_WIDTH, CARD_HEIGHT, SLOT);
            if (pile == STOCK) {
                // Empty stock: a ring meaning "tap to turn the waste over".
                TftTouchShield.drawCircle(x + CARD_WIDTH / 2, TOP_Y + CARD_HEIGHT / 2, 9, SLOT);
                TftTouchShield.drawCircle(x + CARD_WIDTH / 2, TOP_Y + CARD_HEIGHT / 2, 8, SLOT);
            } else if (pile < STOCK) {
                drawSuit(x + 7, TOP_Y + 13, pile - FOUNDATION, 2, SLOT, FELT);
            }
        } else if (pile == STOCK) {
            drawCardBack(x, TOP_Y);
        } else {
            drawCard(x, TOP_Y, cards[pile * CAPACITY + count - 1], pile == selectedPile);
        }
    }

    private static void drawColumn(byte[] cards, int[] meta, int column) {
        int x = 1 + column * COLUMN_WIDTH;
        TftTouchShield.fillRect(x, TABLEAU_Y, CARD_WIDTH, HEIGHT - TABLEAU_Y, FELT);
        int count = meta[TABLEAU + column];
        if (count == 0) {
            TftTouchShield.drawRect(x, TABLEAU_Y, CARD_WIDTH, CARD_HEIGHT, SLOT);
            TftTouchShield.setTextSize(2);
            TftTouchShield.setTextColor(SLOT, FELT);
            TftTouchShield.setCursor(x + 10, TABLEAU_Y + 15);
            TftTouchShield.print("K");
            return;
        }
        int step = faceUpStep(meta, column);
        for (int i = 0; i < count; i++) {
            int y = cardY(meta, column, i, step);
            if (i < meta[HIDDEN + column]) {
                drawCardBack(x, y);
            } else {
                boolean selected = selectedPile == TABLEAU + column && i >= selectedIndex;
                drawCard(x, y, cards[(TABLEAU + column) * CAPACITY + i], selected);
            }
        }
    }

    /** A 32x44 card: rank and small suit along the top edge (all that shows when overlapped), big suit below. */
    private static void drawCard(int x, int y, int card, boolean selected) {
        int rank = card % 13;
        int suit = card / 13;
        int ink = TftTouchShield.BLACK;
        if (isRed(card)) {
            ink = TftTouchShield.RED;
        }
        TftTouchShield.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        int border = TftTouchShield.GRAY;
        if (selected) {
            border = SELECTED;
            TftTouchShield.drawRect(x + 1, y + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, SELECTED);
        }
        TftTouchShield.drawRect(x, y, CARD_WIDTH, CARD_HEIGHT, border);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(ink, TftTouchShield.WHITE);
        TftTouchShield.setCursor(x + 3, y + 3);
        TftTouchShield.print(rankName(rank));
        drawSuit(x + CARD_WIDTH - 12, y + 2, suit, 1, ink, TftTouchShield.WHITE);
        drawSuit(x + 7, y + 19, suit, 2, ink, TftTouchShield.WHITE);
    }

    private static void drawCardBack(int x, int y) {
        TftTouchShield.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.drawRect(x, y, CARD_WIDTH, CARD_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.fillRect(x + 3, y + 3, CARD_WIDTH - 6, CARD_HEIGHT - 6, CARD_BACK);
        TftTouchShield.drawRect(x + 6, y + 6, CARD_WIDTH - 12, CARD_HEIGHT - 12, TftTouchShield.WHITE);
    }

    private static void showBanner(String text, int color) {
        TftTouchShield.fillRect(0, 136, WIDTH, 32, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor((WIDTH - text.length() * 18) / 2, 141);
        TftTouchShield.print(text);
    }

    // Streams a 9x9 suit bitmap scaled by {@code scale}.
    private static void drawSuit(int x, int y, int suit, int scale, int ink, int paper) {
        int size = 9 * scale;
        if (!TftTouchShield.beginPixels(x, y, size, size)) {
            return;
        }
        for (int row = 0; row < 9; row++) {
            int bits = suitRow(suit, row);
            for (int repeatRow = 0; repeatRow < scale; repeatRow++) {
                for (int column = 0; column < 9; column++) {
                    int color = paper;
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
        if (rank == KING) {
            return "K";
        }
        return String.valueOf(rank + 1);
    }

    // 9x9 suit symbols (as in Blackjack), bit n = column n.
    private static int suitRow(int suit, int row) {
        if (suit == 0) {
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
        if (suit == HEARTS) {
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
        if (suit == DIAMONDS) {
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
