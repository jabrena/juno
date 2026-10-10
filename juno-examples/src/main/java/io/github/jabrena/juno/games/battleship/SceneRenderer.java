package io.github.jabrena.juno.games.battleship;

import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.battleship.AutopilotBattleship.*;
import static io.github.jabrena.juno.games.battleship.Battleship.*;
import static io.github.jabrena.juno.games.battleship.Controls.*;
import static io.github.jabrena.juno.games.battleship.Fleet.*;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.*;

final class SceneRenderer {
    private SceneRenderer() {
    }

    static void drawEnemyCell(int cell, int state) {
        int x = GRID_X + (cell % SIZE) * CELL;
        int y = GRID_Y + (cell / SIZE) * CELL;
        int fill = SEA;
        if (state == SUNK) {
            fill = SUNK_COLOR;
        }
        TftTouchShield.fillRect(x, y, CELL, CELL, fill);
        TftTouchShield.drawRect(x, y, CELL + 1, CELL + 1, GRID_LINE);
        int cx = x + CELL / 2;
        int cy = y + CELL / 2;
        if (state == MISS) {
            TftTouchShield.fillCircle(cx, cy, 3, TftTouchShield.WHITE);
        } else if (state == HIT) {
            TftTouchShield.fillCircle(cx, cy, 7, TftTouchShield.RED);
        } else if (state == SUNK) {
            TftTouchShield.fillCircle(cx, cy, 5, TftTouchShield.RED);
        }
    }

    static void drawMyCell(int cell, int ship, int state) {
        int x = MINI_X + (cell % SIZE) * MINI_CELL;
        int y = MINI_Y + (cell / SIZE) * MINI_CELL;
        int color = SEA;
        if (state >= HIT) {
            color = TftTouchShield.RED;
        } else if (state == MISS) {
            color = TftTouchShield.WHITE;
        } else if (ship != 0) {
            color = SHIP;
        }
        TftTouchShield.fillRect(x, y, MINI_CELL - 1, MINI_CELL - 1, color);
    }

    static void drawShipsLeft(byte[] myFleet, byte[] enemyFleet, byte[] myShots, byte[] enemyShots) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(INFO_X, MINI_Y);
        TftTouchShield.print("Your fleet");
        drawShipsLeftCounts(myFleet, enemyShots, true);
        drawShipsLeftCounts(enemyFleet, myShots, false);
    }

    static void drawShipsLeftCounts(byte[] fleet, byte[] shots, boolean mine) {
        int afloat = 0;
        for (int ship = 1; ship <= SHIPS; ship++) {
            if (!isSunk(fleet, shots, ship)) {
                afloat = afloat + 1;
            }
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        if (mine) {
            TftTouchShield.setCursor(INFO_X, MINI_Y + 14);
            TftTouchShield.print("Your ships: ");
        } else {
            TftTouchShield.setCursor(INFO_X, MINI_Y + 26);
            TftTouchShield.print("CPU ships:  ");
        }
        TftTouchShield.print(afloat);
    }

    static void showStatus(String text, int color) {
        showStatus2(text, "", color);
    }

    static void showStatus2(String first, String second, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 8);
        TftTouchShield.print(first);
        TftTouchShield.print(second);
    }
}
