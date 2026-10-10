package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.blackjack.Cards.*;
import static io.github.jabrena.juno.games.blackjack.Controls.*;
import static io.github.jabrena.juno.games.blackjack.Round.*;
import static io.github.jabrena.juno.games.blackjack.SceneRenderer.*;

/**
 * Blackjack against the dealer on the ELEGOO 2.8" TFT touch screen shield.
 *
 * <p>Set your bet with {@code -} and {@code +} (steps of 5), then {@code DEAL}. During a hand, tap
 * {@code HIT}, {@code STAND} or {@code DBL} (double down: double the bet, take exactly one more
 * card; only on your first two cards). The dealer stands on all 17s, a blackjack pays 3:2, and a
 * tie is a push. Splitting and insurance are not offered. You start with 100 chips; when you run
 * out, the session returns to the animated cover.
 *
 * <p>The game uses a single 52-card deck, shuffled with Fisher-Yates and reshuffled when fewer than
 * 15 cards remain. Cards are numbered 0-51: {@code card % 13} is the rank (0 = ace ... 12 = king)
 * and {@code card / 13} the suit (spades, hearts, diamonds, clubs). The suit symbols are 9x9
 * bitmaps, since the display font has no ♠♥♦♣ glyphs.
 *
 * <p>Choose HUMAN to make those decisions or CPU to use an automated hit/stand/double policy.
 * Round flow, cards, controls, rendering, suit sprites and interludes live in focused package
 * collaborators; this class owns the session loop and shared scalar state.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class Blackjack {
    static final int COVER_TIMEOUT_MILLIS = 60_000;
    static final int START_BANK = 100;
    static final int MIN_BET = 5;
    static final int BET_STEP = 5;
    static final int RESHUFFLE_BELOW = 15;
    static final int MAX_CARDS = 12;

    static final int SPADES = 0;
    static final int HEARTS = 1;
    static final int DIAMONDS = 2;

    // Phases.
    static final int BETTING = 0;
    static final int PLAYING = 1;

    // Layout (portrait, 240x320).
    static final int HEADER_HEIGHT = 32;
    static final int CARD_WIDTH = 40;
    static final int CARD_HEIGHT = 56;
    static final int HAND_X = 8;
    static final int DEALER_LABEL_Y = 40;
    static final int DEALER_CARDS_Y = 58;
    static final int MESSAGE_Y = 126;
    static final int PLAYER_LABEL_Y = 156;
    static final int PLAYER_CARDS_Y = 174;
    static final int BUTTON_Y = 262;
    static final int BUTTON_WIDTH = 72;
    static final int BUTTON_HEIGHT = 44;
    static final int BUTTON_SPACING = 76;

    static final int FELT = 0x0366;
    static final int CARD_BACK = 0x1152;
    static final int BUTTON = 0xCD05;
    static final int BUTTON_DISABLED = 0x39E7;
    static final int HEADER_BACKGROUND = 0x2945;

    static int phase;
    static int bank;
    static int bet;
    static int handBet;
    static int deckPosition;
    static int playerCount;
    static int dealerCount;
    static boolean holeHidden;
    static boolean seeded;
    static boolean gameOver;

    private Blackjack() {
    }

    public static void main(String[] args) {
        byte[] deck = new byte[52];
        byte[] player = new byte[MAX_CARDS];
        byte[] dealer = new byte[MAX_CARDS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.cover();
                tapped = Controls.waitForTap(COVER_TIMEOUT_MILLIS);
            }
            Controls.choosePlayer();
            playSession(deck, player, dealer);
            Delay.millis(3000);
        }
    }

    private static void playSession(byte[] deck, byte[] player, byte[] dealer) {
        TftTouchShield.fillScreen(FELT);
        bank = START_BANK;
        bet = 10;
        deckPosition = 52;
        phase = BETTING;
        gameOver = false;
        drawHeader();
        showMessage("Place your bet", TftTouchShield.WHITE);
        drawButtons(player);

        while (!gameOver) {
            int button = Controls.autopilot ? cpuButton(player) : waitForButton();
            if (button == 3) {
                Controls.autopilot = !Controls.autopilot;
                showMessage(Controls.autopilot ? "CPU player" : "Human player", TftTouchShield.CYAN);
                continue;
            }
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

    private static int cpuButton(byte[] player) {
        Delay.millis(550);
        if (phase == BETTING) {
            return 2;
        }
        int value = handValue(player, playerCount);
        if (canDouble() && value >= 9 && value <= 11) {
            return 2;
        }
        return value < 17 ? 0 : 1;
    }
}
