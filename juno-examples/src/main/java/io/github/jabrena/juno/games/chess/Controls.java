package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import static io.github.jabrena.juno.games.chess.Chess.BOARD_X;
import static io.github.jabrena.juno.games.chess.Chess.BOARD_Y;
import static io.github.jabrena.juno.games.chess.Chess.CELL;

final class Controls {
    private static final Clock CLOCK = ArduinoUno.getClock();
    static boolean autopilot;

    private Controls() {
    }

    static int squareAt(int x, int y) {
        int column = (x - BOARD_X) / CELL;
        int row = (y - BOARD_Y) / CELL;
        if (x < BOARD_X || y < BOARD_Y || column > 7 || row > 7) {
            return -1;
        }
        return row * 8 + column;
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
        tft.fillScreen(0x0841);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, 0x0841);
        tft.setCursor(42, 72);
        tft.print("WHO PLAYS WHITE?");
        drawChoice(tft, 18, "HUMAN", "You move", TftTouchShield.CYAN);
        drawChoice(tft, 126, "CPU", "Auto match", TftTouchShield.ORANGE);
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

    private static void drawChoice(TftTouchShield tft, int x, String label, String detail, int color) {
        tft.fillRect(x, 132, 96, 94, color);
        tft.fillRect(x + 3, 135, 90, 88, 0x0841);
        tft.setTextColor(color, 0x0841);
        tft.setTextSize(2);
        tft.setCursor(x + 12, 158);
        tft.print(label);
        tft.setTextSize(1);
        tft.setCursor(x + 18, 193);
        tft.print(detail);
    }
}
