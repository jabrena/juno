package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.blackjack.Blackjack.*;
import static io.github.jabrena.juno.games.blackjack.Round.*;
import static io.github.jabrena.juno.games.blackjack.Cards.*;
import static io.github.jabrena.juno.games.blackjack.Controls.*;
import static io.github.jabrena.juno.games.blackjack.SceneRenderer.*;
import static io.github.jabrena.juno.games.blackjack.SuitSprites.*;
import static io.github.jabrena.juno.games.blackjack.SpadeSprite.*;
import static io.github.jabrena.juno.games.blackjack.HeartSprite.*;
import static io.github.jabrena.juno.games.blackjack.DiamondSprite.*;
import static io.github.jabrena.juno.games.blackjack.ClubSprite.*;

final class Round {
    private Round() {
    }

    static void deal(byte[] deck, byte[] player, byte[] dealer) {
        if (!seeded) {
            Random.seed(Clock.micros());
            seeded = true;
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

    static void hit(byte[] deck, byte[] player, byte[] dealer) {
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

    static boolean canDouble() {
        return playerCount == 2 && bank >= handBet;
    }

    static void doubleDown(byte[] deck, byte[] player, byte[] dealer) {
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

    static void dealerTurn(byte[] deck, byte[] player, byte[] dealer) {
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

    static void revealHole(byte[] dealer) {
        if (holeHidden) {
            holeHidden = false;
            drawHand(dealer, dealerCount, false, DEALER_LABEL_Y, DEALER_CARDS_Y, "Dealer");
        }
    }

    static void settle(int payout, String message, int color, byte[] player) {
        bank = bank + payout;
        phase = BETTING;
        if (bank < MIN_BET) {
            showMessage("Out of chips!", TftTouchShield.RED);
            gameOver = true;
        } else {
            showMessage(message, color);
        }
        bet = Math.max(MIN_BET, Math.min(bet, bank));
        drawHeader();
        if (!gameOver) {
            drawButtons(player);
        }
    }
}
