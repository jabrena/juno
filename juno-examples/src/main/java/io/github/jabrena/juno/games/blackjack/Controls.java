package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
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

final class Controls {
    static boolean autopilot;

    private Controls() {
    }

    static int waitForButton() {
        while (true) {
            if (TftTouchShield.readTouch()) {
                int x = TftTouchShield.touchX();
                int y = TftTouchShield.touchY();
                waitForRelease();
                if (y < HEADER_HEIGHT) {
                    return 3;
                }
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

    static void waitForRelease() {
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

    static boolean waitForTap(int timeoutMillis) {
        int started = Clock.millis();
        while (Clock.millis() - started < timeoutMillis) {
            if (TftTouchShield.readTouch()) {
                waitForRelease();
                return true;
            }
            Delay.millis(10);
        }
        return false;
    }

    static void choosePlayer() {
        TftTouchShield.fillScreen(0x0366);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, 0x0366);
        TftTouchShield.setCursor(48, 74);
        TftTouchShield.print("CHOOSE PLAYER");
        drawChoice(18, "HUMAN", TftTouchShield.CYAN);
        drawChoice(126, "CPU", TftTouchShield.ORANGE);
        while (true) {
            if (TftTouchShield.readTouch()) {
                int x = TftTouchShield.touchX();
                int y = TftTouchShield.touchY();
                waitForRelease();
                if (y >= 132 && y < 226) {
                    autopilot = x >= 120;
                    return;
                }
            }
            Delay.millis(10);
        }
    }

    private static void drawChoice(int x, String label, int color) {
        TftTouchShield.fillRect(x, 132, 96, 94, color);
        TftTouchShield.fillRect(x + 3, 135, 90, 88, 0x0366);
        TftTouchShield.setTextColor(color, 0x0366);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(x + 12, 164);
        TftTouchShield.print(label);
    }
}
