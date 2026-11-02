package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;

import static io.github.jabrena.juno.games.blackjack.Blackjack.BETTING;
import static io.github.jabrena.juno.games.blackjack.Blackjack.DEALER_CARDS_Y;
import static io.github.jabrena.juno.games.blackjack.Blackjack.DEALER_LABEL_Y;
import static io.github.jabrena.juno.games.blackjack.Blackjack.MAX_CARDS;
import static io.github.jabrena.juno.games.blackjack.Blackjack.MIN_BET;
import static io.github.jabrena.juno.games.blackjack.Blackjack.PLAYER_CARDS_Y;
import static io.github.jabrena.juno.games.blackjack.Blackjack.PLAYER_LABEL_Y;
import static io.github.jabrena.juno.games.blackjack.Blackjack.PLAYING;
import static io.github.jabrena.juno.games.blackjack.Blackjack.RESHUFFLE_BELOW;
import static io.github.jabrena.juno.games.blackjack.Blackjack.bank;
import static io.github.jabrena.juno.games.blackjack.Blackjack.bet;
import static io.github.jabrena.juno.games.blackjack.Blackjack.dealerCount;
import static io.github.jabrena.juno.games.blackjack.Blackjack.deckPosition;
import static io.github.jabrena.juno.games.blackjack.Blackjack.gameOver;
import static io.github.jabrena.juno.games.blackjack.Blackjack.handBet;
import static io.github.jabrena.juno.games.blackjack.Blackjack.holeHidden;
import static io.github.jabrena.juno.games.blackjack.Blackjack.phase;
import static io.github.jabrena.juno.games.blackjack.Blackjack.playerCount;
import static io.github.jabrena.juno.games.blackjack.Blackjack.seeded;
import static io.github.jabrena.juno.games.blackjack.Cards.draw;
import static io.github.jabrena.juno.games.blackjack.Cards.handValue;
import static io.github.jabrena.juno.games.blackjack.Cards.shuffle;
import static io.github.jabrena.juno.games.blackjack.SceneRenderer.clearHands;
import static io.github.jabrena.juno.games.blackjack.SceneRenderer.drawButtons;
import static io.github.jabrena.juno.games.blackjack.SceneRenderer.drawHand;
import static io.github.jabrena.juno.games.blackjack.SceneRenderer.drawHeader;
import static io.github.jabrena.juno.games.blackjack.SceneRenderer.showMessage;

final class Round {
    private static final Clock CLOCK = ArduinoUno.getClock();
    private Round() {
    }

    static void deal(TftTouchShield tft, byte[] deck, byte[] player, byte[] dealer) {
        if (!seeded) {
            Random.seed(CLOCK.micros());
            seeded = true;
        }
        bet = Math.min(bet, bank);
        if (deckPosition > 52 - RESHUFFLE_BELOW) {
            shuffle(deck);
            showMessage(tft, "Shuffling...", TftTouchShield.YELLOW);
            Delay.millis(600);
        }
        handBet = bet;
        bank = bank - handBet;
        drawHeader(tft);
        showMessage(tft, "", TftTouchShield.WHITE);

        playerCount = 0;
        dealerCount = 0;
        holeHidden = true;
        clearHands(tft);
        player[playerCount] = draw(deck);
        playerCount = playerCount + 1;
        drawHand(tft, player, playerCount, false, PLAYER_LABEL_Y, PLAYER_CARDS_Y, "You");
        Delay.millis(250);
        dealer[dealerCount] = draw(deck);
        dealerCount = dealerCount + 1;
        drawHand(tft, dealer, dealerCount, false, DEALER_LABEL_Y, DEALER_CARDS_Y, "Dealer");
        Delay.millis(250);
        player[playerCount] = draw(deck);
        playerCount = playerCount + 1;
        drawHand(tft, player, playerCount, false, PLAYER_LABEL_Y, PLAYER_CARDS_Y, "You");
        Delay.millis(250);
        dealer[dealerCount] = draw(deck);
        dealerCount = dealerCount + 1;
        drawHand(tft, dealer, dealerCount, true, DEALER_LABEL_Y, DEALER_CARDS_Y, "Dealer");

        boolean playerBlackjack = handValue(player, playerCount) == 21;
        boolean dealerBlackjack = handValue(dealer, dealerCount) == 21;
        if (playerBlackjack || dealerBlackjack) {
            revealHole(tft, dealer);
            if (playerBlackjack && dealerBlackjack) {
                settle(tft, handBet, "Push", TftTouchShield.WHITE, player);
            } else if (playerBlackjack) {
                settle(tft, handBet + handBet * 3 / 2, "Blackjack!", TftTouchShield.YELLOW, player);
            } else {
                settle(tft, 0, "Dealer blackjack", TftTouchShield.RED, player);
            }
            return;
        }
        phase = PLAYING;
        drawButtons(tft, player);
    }

    static void hit(TftTouchShield tft, byte[] deck, byte[] player, byte[] dealer) {
        player[playerCount] = draw(deck);
        playerCount = playerCount + 1;
        drawHand(tft, player, playerCount, false, PLAYER_LABEL_Y, PLAYER_CARDS_Y, "You");
        int value = handValue(player, playerCount);
        if (value > 21) {
            revealHole(tft, dealer);
            settle(tft, 0, "Bust!", TftTouchShield.RED, player);
        } else if (value == 21 || playerCount == MAX_CARDS) {
            dealerTurn(tft, deck, player, dealer);
        } else {
            drawButtons(tft, player);
        }
    }

    static boolean canDouble() {
        return playerCount == 2 && bank >= handBet;
    }

    static void doubleDown(TftTouchShield tft, byte[] deck, byte[] player, byte[] dealer) {
        bank = bank - handBet;
        handBet = handBet * 2;
        drawHeader(tft);
        player[playerCount] = draw(deck);
        playerCount = playerCount + 1;
        drawHand(tft, player, playerCount, false, PLAYER_LABEL_Y, PLAYER_CARDS_Y, "You");
        if (handValue(player, playerCount) > 21) {
            revealHole(tft, dealer);
            settle(tft, 0, "Bust!", TftTouchShield.RED, player);
            return;
        }
        dealerTurn(tft, deck, player, dealer);
    }

    static void dealerTurn(TftTouchShield tft, byte[] deck, byte[] player, byte[] dealer) {
        revealHole(tft, dealer);
        while (handValue(dealer, dealerCount) < 17 && dealerCount < MAX_CARDS) {
            Delay.millis(600);
            dealer[dealerCount] = draw(deck);
            dealerCount = dealerCount + 1;
            drawHand(tft, dealer, dealerCount, false, DEALER_LABEL_Y, DEALER_CARDS_Y, "Dealer");
        }
        int mine = handValue(player, playerCount);
        int theirs = handValue(dealer, dealerCount);
        if (theirs > 21) {
            settle(tft, handBet * 2, "Dealer busts!", TftTouchShield.GREEN, player);
        } else if (mine > theirs) {
            settle(tft, handBet * 2, "You win!", TftTouchShield.GREEN, player);
        } else if (mine == theirs) {
            settle(tft, handBet, "Push", TftTouchShield.WHITE, player);
        } else {
            settle(tft, 0, "Dealer wins", TftTouchShield.RED, player);
        }
    }

    static void revealHole(TftTouchShield tft, byte[] dealer) {
        if (holeHidden) {
            holeHidden = false;
            drawHand(tft, dealer, dealerCount, false, DEALER_LABEL_Y, DEALER_CARDS_Y, "Dealer");
        }
    }

    static void settle(TftTouchShield tft, int payout, String message, int color, byte[] player) {
        bank = bank + payout;
        phase = BETTING;
        if (bank < MIN_BET) {
            showMessage(tft, "Out of chips!", TftTouchShield.RED);
            gameOver = true;
        } else {
            showMessage(tft, message, color);
        }
        bet = Math.max(MIN_BET, Math.min(bet, bank));
        drawHeader(tft);
        if (!gameOver) {
            drawButtons(tft, player);
        }
    }
}
