package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.BUTTON_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.HEADER_HEIGHT;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.NEW_X;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.ROW1_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.ROW2_Y;
import static io.github.jabrena.juno.games.texasholdem.TexasHoldem.WIDTH;

final class Controls {
    private static final Clock CLOCK = ArduinoUno.getClock();
    static boolean autopilot;

    private Controls() {
    }

    static int waitForButton(TftTouchShield tft) {
        while (true) {
            if (tft.readTouch()) {
                int x = tft.touchX();
                int y = tft.touchY();
                waitForRelease(tft);
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

    static boolean waitForDeal(TftTouchShield tft) {
        while (true) {
            int button = waitForButton(tft);
            if (button == 6) {
                return true;
            }
            if (button <= 2) {
                return false;
            }
        }
    }

    static void waitForNew(TftTouchShield tft) {
        while (waitForButton(tft) != 6) {
            Delay.millis(10);
        }
    }

    static void waitForRelease(TftTouchShield tft) {
        int misses = 0;
        while (misses < 3) {
            if (tft.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }

    static boolean waitForTap(TftTouchShield tft, int timeoutMillis) {
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < timeoutMillis) {
            if (tft.readTouch()) {
                waitForRelease(tft);
                return true;
            }
            Delay.millis(10);
        }
        return false;
    }

    static void choosePlayer(TftTouchShield tft) {
        tft.fillScreen(0x0366);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, 0x0366);
        tft.setCursor(48, 74);
        tft.print("CHOOSE PLAYER");
        drawChoice(tft, 18, "HUMAN", TftTouchShield.CYAN);
        drawChoice(tft, 126, "CPU", TftTouchShield.ORANGE);
        while (true) {
            if (tft.readTouch()) {
                int x = tft.touchX();
                int y = tft.touchY();
                waitForRelease(tft);
                if (y >= 132 && y < 226) {
                    autopilot = x >= 120;
                    return;
                }
            }
            Delay.millis(10);
        }
    }

    private static void drawChoice(TftTouchShield tft, int x, String label, int color) {
        tft.fillRect(x, 132, 96, 94, color);
        tft.fillRect(x + 3, 135, 90, 88, 0x0366);
        tft.setTextColor(color, 0x0366);
        tft.setTextSize(2);
        tft.setCursor(x + 12, 164);
        tft.print(label);
    }
}
