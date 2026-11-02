package io.github.jabrena.juno.games.battleship;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;


final class Controls {
    private static final Clock CLOCK = ArduinoUno.getClock();
    static boolean autopilot;

    private Controls() {
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

    static void chooseCommander(TftTouchShield tft) {
        tft.fillScreen(0x0012);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, 0x0012);
        tft.setCursor(30, 74);
        tft.print("CHOOSE COMMANDER");
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
        tft.fillRect(x + 3, 135, 90, 88, 0x0012);
        tft.setTextColor(color, 0x0012);
        tft.setTextSize(2);
        tft.setCursor(x + 12, 164);
        tft.print(label);
    }
}
