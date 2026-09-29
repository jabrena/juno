package io.github.jabrena.juno.games.battleship;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.battleship.Battleship.*;
import static io.github.jabrena.juno.games.battleship.Fleet.*;
import static io.github.jabrena.juno.games.battleship.AutopilotBattleship.*;
import static io.github.jabrena.juno.games.battleship.Controls.*;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.*;

final class Controls {
    static boolean autopilot;

    private Controls() {
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

    static void chooseCommander() {
        TftTouchShield.fillScreen(0x0012);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, 0x0012);
        TftTouchShield.setCursor(30, 74);
        TftTouchShield.print("CHOOSE COMMANDER");
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
        TftTouchShield.fillRect(x + 3, 135, 90, 88, 0x0012);
        TftTouchShield.setTextColor(color, 0x0012);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(x + 12, 164);
        TftTouchShield.print(label);
    }
}
