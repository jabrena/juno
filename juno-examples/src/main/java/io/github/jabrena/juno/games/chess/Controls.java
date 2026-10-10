package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.chess.BishopSprite.*;
import static io.github.jabrena.juno.games.chess.Chess.*;
import static io.github.jabrena.juno.games.chess.ChessEngine.*;
import static io.github.jabrena.juno.games.chess.ChessRules.*;
import static io.github.jabrena.juno.games.chess.Controls.*;
import static io.github.jabrena.juno.games.chess.KingSprite.*;
import static io.github.jabrena.juno.games.chess.KnightSprite.*;
import static io.github.jabrena.juno.games.chess.PawnSprite.*;
import static io.github.jabrena.juno.games.chess.PieceSprites.*;
import static io.github.jabrena.juno.games.chess.QueenSprite.*;
import static io.github.jabrena.juno.games.chess.RookSprite.*;
import static io.github.jabrena.juno.games.chess.SceneRenderer.*;

final class Controls {
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
        TftTouchShield.fillScreen(0x0841);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, 0x0841);
        TftTouchShield.setCursor(42, 72);
        TftTouchShield.print("WHO PLAYS WHITE?");
        drawChoice(18, "HUMAN", "You move", TftTouchShield.CYAN);
        drawChoice(126, "CPU", "Auto match", TftTouchShield.ORANGE);
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

    private static void drawChoice(int x, String label, String detail, int color) {
        TftTouchShield.fillRect(x, 132, 96, 94, color);
        TftTouchShield.fillRect(x + 3, 135, 90, 88, 0x0841);
        TftTouchShield.setTextColor(color, 0x0841);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(x + 12, 158);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(x + 18, 193);
        TftTouchShield.print(detail);
    }
}
