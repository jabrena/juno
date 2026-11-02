package io.github.jabrena.juno.games.battleship;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

import static io.github.jabrena.juno.games.battleship.Battleship.CELL;
import static io.github.jabrena.juno.games.battleship.Battleship.GRID_LINE;
import static io.github.jabrena.juno.games.battleship.Battleship.GRID_X;
import static io.github.jabrena.juno.games.battleship.Battleship.GRID_Y;
import static io.github.jabrena.juno.games.battleship.Battleship.HEADER_BACKGROUND;
import static io.github.jabrena.juno.games.battleship.Battleship.HEADER_HEIGHT;
import static io.github.jabrena.juno.games.battleship.Battleship.HIT;
import static io.github.jabrena.juno.games.battleship.Battleship.INFO_X;
import static io.github.jabrena.juno.games.battleship.Battleship.MINI_CELL;
import static io.github.jabrena.juno.games.battleship.Battleship.MINI_X;
import static io.github.jabrena.juno.games.battleship.Battleship.MINI_Y;
import static io.github.jabrena.juno.games.battleship.Battleship.MISS;
import static io.github.jabrena.juno.games.battleship.Battleship.SEA;
import static io.github.jabrena.juno.games.battleship.Battleship.SHIP;
import static io.github.jabrena.juno.games.battleship.Battleship.SHIPS;
import static io.github.jabrena.juno.games.battleship.Battleship.SIZE;
import static io.github.jabrena.juno.games.battleship.Battleship.SUNK;
import static io.github.jabrena.juno.games.battleship.Battleship.SUNK_COLOR;
import static io.github.jabrena.juno.games.battleship.Fleet.isSunk;

final class SceneRenderer {
    private SceneRenderer() {
    }

    static void drawEnemyCell(TftTouchShield tft, int cell, int state) {
        int x = GRID_X + (cell % SIZE) * CELL;
        int y = GRID_Y + (cell / SIZE) * CELL;
        int fill = SEA;
        if (state == SUNK) {
            fill = SUNK_COLOR;
        }
        tft.fillRect(x, y, CELL, CELL, fill);
        tft.drawRect(x, y, CELL + 1, CELL + 1, GRID_LINE);
        int cx = x + CELL / 2;
        int cy = y + CELL / 2;
        if (state == MISS) {
            tft.fillCircle(cx, cy, 3, TftTouchShield.WHITE);
        } else if (state == HIT) {
            tft.fillCircle(cx, cy, 7, TftTouchShield.RED);
        } else if (state == SUNK) {
            tft.fillCircle(cx, cy, 5, TftTouchShield.RED);
        }
    }

    static void drawMyCell(TftTouchShield tft, int cell, int ship, int state) {
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
        tft.fillRect(x, y, MINI_CELL - 1, MINI_CELL - 1, color);
    }

    static void drawShipsLeft(TftTouchShield tft, byte[] myFleet, byte[] enemyFleet, byte[] myShots, byte[] enemyShots) {
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        tft.setCursor(INFO_X, MINI_Y);
        tft.print("Your fleet");
        drawShipsLeftCounts(tft, myFleet, enemyShots, true);
        drawShipsLeftCounts(tft, enemyFleet, myShots, false);
    }

    static void drawShipsLeftCounts(TftTouchShield tft, byte[] fleet, byte[] shots, boolean mine) {
        int afloat = 0;
        for (int ship = 1; ship <= SHIPS; ship++) {
            if (!isSunk(fleet, shots, ship)) {
                afloat = afloat + 1;
            }
        }
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        if (mine) {
            tft.setCursor(INFO_X, MINI_Y + 14);
            tft.print("Your ships: ");
        } else {
            tft.setCursor(INFO_X, MINI_Y + 26);
            tft.print("CPU ships:  ");
        }
        tft.print(afloat);
    }

    static void showStatus(TftTouchShield tft, String text, int color) {
        showStatus2(tft, text, "", color);
    }

    static void showStatus2(TftTouchShield tft, String first, String second, int color) {
        tft.fillRect(0, 0, tft.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        tft.setTextSize(2);
        tft.setTextColor(color, HEADER_BACKGROUND);
        tft.setCursor(8, 8);
        tft.print(first);
        tft.print(second);
    }
}
