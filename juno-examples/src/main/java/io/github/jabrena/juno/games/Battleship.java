package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Battleship against the computer on the ELEGOO 2.8" TFT touch screen shield. Each side hides a
 * fleet — carrier (5), battleship (4), cruiser (3), submarine (3) and destroyer (2) — on a 10x10
 * grid; both fleets are placed at random. Tap a square of the large enemy grid to fire; the
 * computer then fires back at your fleet, shown in the small grid below. Sink the whole enemy fleet
 * first to win. {@code NEW} deals new fleets.
 *
 * <p>Enemy grid: white dot = miss, red = hit, dark red = sunk ship. Your grid: gray = your ships,
 * red = hit, white = the computer's misses.
 *
 * <p>The computer hunts on a checkerboard pattern (every ship covers at least one such square),
 * then, after a hit, targets the neighbouring squares, following the line once two hits are in a
 * row, until the ship sinks.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Battleship {
    private static final int SIZE = 10;
    private static final int CELLS = SIZE * SIZE;
    private static final int SHIPS = 5;

    // Shot states.
    private static final int UNKNOWN = 0;
    private static final int MISS = 1;
    private static final int HIT = 2;
    private static final int SUNK = 3;

    // Layout (portrait, 240x320).
    private static final int HEADER_HEIGHT = 32;
    private static final int CELL = 20;
    private static final int GRID_X = 20;
    private static final int GRID_Y = 36;
    private static final int MINI_CELL = 7;
    private static final int MINI_X = 8;
    private static final int MINI_Y = 244;
    private static final int INFO_X = 90;
    private static final int NEW_X = 172;
    private static final int NEW_Y = 286;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 28;

    private static final int SEA = 0x0012;
    private static final int GRID_LINE = 0x0218;
    private static final int SHIP = 0x8410;
    private static final int SUNK_COLOR = 0x7800;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static boolean gameOver;
    private static boolean seeded;

    private Battleship() {
    }

    public static void main(String[] args) {
        byte[] myFleet = new byte[CELLS];
        byte[] enemyFleet = new byte[CELLS];
        byte[] myShots = new byte[CELLS];
        byte[] enemyShots = new byte[CELLS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        newGame(myFleet, enemyFleet, myShots, enemyShots);

        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            waitForRelease();

            if (x >= NEW_X && y >= NEW_Y) {
                newGame(myFleet, enemyFleet, myShots, enemyShots);
                continue;
            }
            if (gameOver || x < GRID_X || y < GRID_Y || x >= GRID_X + SIZE * CELL || y >= GRID_Y + SIZE * CELL) {
                continue;
            }
            int cell = ((y - GRID_Y) / CELL) * SIZE + (x - GRID_X) / CELL;
            if (myShots[cell] != UNKNOWN) {
                continue;
            }
            playerShot(cell, enemyFleet, myShots);
            if (gameOver) {
                continue;
            }
            Delay.millis(500);
            computerShot(myFleet, enemyShots);
        }
    }

    // ---- Game flow ----

    private static void newGame(byte[] myFleet, byte[] enemyFleet, byte[] myShots, byte[] enemyShots) {
        if (!seeded) {
            Random.seed(Clock.micros());
            seeded = true;
        }
        placeFleet(myFleet);
        placeFleet(enemyFleet);
        for (int i = 0; i < CELLS; i++) {
            myShots[i] = UNKNOWN;
            enemyShots[i] = UNKNOWN;
        }
        gameOver = false;

        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        for (int i = 0; i < CELLS; i++) {
            drawEnemyCell(i, UNKNOWN);
            drawMyCell(i, myFleet[i], UNKNOWN);
        }
        TftTouchShield.fillRect(NEW_X, NEW_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(NEW_X + 12, NEW_Y + 7);
        TftTouchShield.print("NEW");
        drawShipsLeft(myFleet, enemyFleet, myShots, enemyShots);
        showStatus("Fire at will!", TftTouchShield.WHITE);
    }

    private static void playerShot(int cell, byte[] enemyFleet, byte[] myShots) {
        int ship = enemyFleet[cell];
        if (ship == 0) {
            myShots[cell] = MISS;
            drawEnemyCell(cell, MISS);
            showStatus("Miss", TftTouchShield.GRAY);
            return;
        }
        myShots[cell] = HIT;
        drawEnemyCell(cell, HIT);
        if (!isSunk(enemyFleet, myShots, ship)) {
            showStatus("Hit!", TftTouchShield.RED);
            return;
        }
        markSunk(enemyFleet, myShots, ship);
        for (int i = 0; i < CELLS; i++) {
            if (enemyFleet[i] == ship) {
                drawEnemyCell(i, SUNK);
            }
        }
        showStatus2("You sank ", shipName(ship), TftTouchShield.YELLOW);
        drawShipsLeftCounts(enemyFleet, myShots, false);
        if (fleetDestroyed(enemyFleet, myShots)) {
            gameOver = true;
            showStatus("You win!", TftTouchShield.GREEN);
        }
    }

    private static void computerShot(byte[] myFleet, byte[] enemyShots) {
        int cell = chooseTarget(enemyShots);
        int ship = myFleet[cell];
        if (ship == 0) {
            enemyShots[cell] = MISS;
            drawMyCell(cell, 0, MISS);
        } else {
            enemyShots[cell] = HIT;
            drawMyCell(cell, ship, HIT);
            if (isSunk(myFleet, enemyShots, ship)) {
                markSunk(myFleet, enemyShots, ship);
                showStatus2("CPU sank ", shipName(ship), TftTouchShield.RED);
                if (fleetDestroyed(myFleet, enemyShots)) {
                    gameOver = true;
                    showStatus("Computer wins", TftTouchShield.RED);
                }
            }
        }
        drawShipsLeftCounts(myFleet, enemyShots, true);
    }

    // ---- Fleets ----

    private static int shipLength(int ship) {
        if (ship == 1) {
            return 5;
        }
        if (ship == 2) {
            return 4;
        }
        if (ship == 5) {
            return 2;
        }
        return 3;
    }

    private static String shipName(int ship) {
        if (ship == 1) {
            return "Carrier";
        }
        if (ship == 2) {
            return "Battleship";
        }
        if (ship == 3) {
            return "Cruiser";
        }
        if (ship == 4) {
            return "Submarine";
        }
        return "Destroyer";
    }

    private static void placeFleet(byte[] fleet) {
        for (int i = 0; i < CELLS; i++) {
            fleet[i] = 0;
        }
        for (int ship = 1; ship <= SHIPS; ship++) {
            int length = shipLength(ship);
            boolean placed = false;
            while (!placed) {
                boolean horizontal = Random.nextInt(2) == 0;
                int row = Random.nextInt(SIZE);
                int column = Random.nextInt(SIZE);
                int step = SIZE;
                if (horizontal) {
                    step = 1;
                    column = Random.nextInt(SIZE - length + 1);
                } else {
                    row = Random.nextInt(SIZE - length + 1);
                }
                int start = row * SIZE + column;
                boolean free = true;
                for (int i = 0; i < length; i++) {
                    if (fleet[start + i * step] != 0) {
                        free = false;
                    }
                }
                if (free) {
                    for (int i = 0; i < length; i++) {
                        fleet[start + i * step] = (byte) ship;
                    }
                    placed = true;
                }
            }
        }
    }

    private static boolean isSunk(byte[] fleet, byte[] shots, int ship) {
        for (int i = 0; i < CELLS; i++) {
            if (fleet[i] == ship && shots[i] < HIT) {
                return false;
            }
        }
        return true;
    }

    private static void markSunk(byte[] fleet, byte[] shots, int ship) {
        for (int i = 0; i < CELLS; i++) {
            if (fleet[i] == ship) {
                shots[i] = SUNK;
            }
        }
    }

    private static boolean fleetDestroyed(byte[] fleet, byte[] shots) {
        for (int i = 0; i < CELLS; i++) {
            if (fleet[i] != 0 && shots[i] < HIT) {
                return false;
            }
        }
        return true;
    }

    // ---- Computer targeting ----

    /** Picks the computer's next shot: extend a line of hits, else probe around a hit, else hunt. */
    private static int chooseTarget(byte[] shots) {
        for (int cell = 0; cell < CELLS; cell++) {
            if (shots[cell] != HIT) {
                continue;
            }
            for (int direction = 0; direction < 4; direction++) {
                int next = neighbour(cell, direction);
                if (next >= 0 && shots[next] == HIT) {
                    int end = next;
                    while (end >= 0 && shots[end] == HIT) {
                        end = neighbour(end, direction);
                    }
                    if (end >= 0 && shots[end] == UNKNOWN) {
                        return end;
                    }
                }
            }
        }
        for (int cell = 0; cell < CELLS; cell++) {
            if (shots[cell] != HIT) {
                continue;
            }
            int start = Random.nextInt(4);
            for (int i = 0; i < 4; i++) {
                int next = neighbour(cell, (start + i) % 4);
                if (next >= 0 && shots[next] == UNKNOWN) {
                    return next;
                }
            }
        }
        int candidates = 0;
        for (int cell = 0; cell < CELLS; cell++) {
            if (shots[cell] == UNKNOWN && ((cell / SIZE + cell % SIZE) % 2 == 0)) {
                candidates = candidates + 1;
            }
        }
        boolean checkerboard = candidates > 0;
        if (!checkerboard) {
            for (int cell = 0; cell < CELLS; cell++) {
                if (shots[cell] == UNKNOWN) {
                    candidates = candidates + 1;
                }
            }
        }
        int pick = Random.nextInt(candidates);
        for (int cell = 0; cell < CELLS; cell++) {
            if (shots[cell] == UNKNOWN && (!checkerboard || (cell / SIZE + cell % SIZE) % 2 == 0)) {
                if (pick == 0) {
                    return cell;
                }
                pick = pick - 1;
            }
        }
        return 0;
    }

    /** The adjacent cell in direction 0 (up), 1 (right), 2 (down) or 3 (left), or -1 off the grid. */
    private static int neighbour(int cell, int direction) {
        int row = cell / SIZE;
        int column = cell % SIZE;
        if (direction == 0) {
            row = row - 1;
        } else if (direction == 1) {
            column = column + 1;
        } else if (direction == 2) {
            row = row + 1;
        } else {
            column = column - 1;
        }
        if (row < 0 || row >= SIZE || column < 0 || column >= SIZE) {
            return -1;
        }
        return row * SIZE + column;
    }

    // ---- Input ----

    private static void waitForRelease() {
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

    // ---- Drawing ----

    private static void drawEnemyCell(int cell, int state) {
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

    private static void drawMyCell(int cell, int ship, int state) {
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

    private static void drawShipsLeft(byte[] myFleet, byte[] enemyFleet, byte[] myShots, byte[] enemyShots) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(INFO_X, MINI_Y);
        TftTouchShield.print("Your fleet");
        drawShipsLeftCounts(myFleet, enemyShots, true);
        drawShipsLeftCounts(enemyFleet, myShots, false);
    }

    private static void drawShipsLeftCounts(byte[] fleet, byte[] shots, boolean mine) {
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

    private static void showStatus(String text, int color) {
        showStatus2(text, "", color);
    }

    private static void showStatus2(String first, String second, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 8);
        TftTouchShield.print(first);
        TftTouchShield.print(second);
    }
}
