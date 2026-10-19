package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.util.Delay;

import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.BUTTON_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.HEADER_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.NEW_X;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.ROW1_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.ROW2_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.WIDTH;

final class Controls {
    private static final Clock CLOCK = MCU.getClock();
    static boolean autopilot;

    private Controls() {
    }

    static int waitForButton() {
        while (true) {
            if (TftTouchShield.readTouch()) {
                int x = TftTouchShield.touchX();
                int y = TftTouchShield.touchY();
                waitForRelease();
                if (y < HEADER_HEIGHT && x >= NEW_X) {
                    return 6;
                }
                if (y >= ROW2_Y) {
                    return Math.min(2, x * 3 / WIDTH);
                }
                if (y >= ROW1_Y && y < ROW1_Y + BUTTON_HEIGHT) {
                    if (x < 44) {
                        return 3;
                    }
                    if (x >= 132 && x < 172) {
                        return 4;
                    }
                    if (x >= 176) {
                        return 5;
                    }
                }
            }
            Delay.millis(10);
        }
    }

    static boolean waitForDeal() {
        while (true) {
            int button = waitForButton();
            if (button == 6) {
                return true;
            }
            if (button <= 2) {
                return false;
            }
        }
    }

    static void waitForNew() {
        while (waitForButton() != 6) {
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
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < timeoutMillis) {
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
