package io.github.jabrena.juno.games.battleship;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.battleship.Fleet.*;
import static io.github.jabrena.juno.games.battleship.AutopilotBattleship.*;
import static io.github.jabrena.juno.games.battleship.Controls.*;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.*;

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
 *
 * <p>An animated radar cover leads to a HUMAN/CPU commander choice. CPU mode uses the same
 * targeting strategy for both fleets, and either result returns to the cover. The package splits
 * fleet rules, targeting, controls, rendering and interludes from this game-loop orchestrator.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class Battleship {
    static final int COVER_TIMEOUT_MILLIS = 60_000;
    static final int SIZE = 10;
    static final int CELLS = SIZE * SIZE;
    static final int SHIPS = 5;

    // Shot states.
    static final int UNKNOWN = 0;
    static final int MISS = 1;
    static final int HIT = 2;
    static final int SUNK = 3;

    // Layout (portrait, 240x320).
    static final int HEADER_HEIGHT = 32;
    static final int CELL = 20;
    static final int GRID_X = 20;
    static final int GRID_Y = 36;
    static final int MINI_CELL = 7;
    static final int MINI_X = 8;
    static final int MINI_Y = 244;
    static final int INFO_X = 90;
    static final int NEW_X = 172;
    static final int NEW_Y = 286;
    static final int BUTTON_WIDTH = 60;
    static final int BUTTON_HEIGHT = 28;

    static final int SEA = 0x0012;
    static final int GRID_LINE = 0x0218;
    static final int SHIP = 0x8410;
    static final int SUNK_COLOR = 0x7800;
    static final int HEADER_BACKGROUND = 0x2945;

    static boolean gameOver;
    static boolean seeded;

    private Battleship() {
    }

    public static void main(String[] args) {
        byte[] myFleet = new byte[CELLS];
        byte[] enemyFleet = new byte[CELLS];
        byte[] myShots = new byte[CELLS];
        byte[] enemyShots = new byte[CELLS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.cover();
                tapped = Controls.waitForTap(COVER_TIMEOUT_MILLIS);
            }
            Controls.chooseCommander();
            newGame(myFleet, enemyFleet, myShots, enemyShots);
            playGame(myFleet, enemyFleet, myShots, enemyShots);
            Delay.millis(3000);
        }
    }

    private static void playGame(byte[] myFleet, byte[] enemyFleet, byte[] myShots, byte[] enemyShots) {
        while (!gameOver) {
            if (Controls.autopilot) {
                playerShot(chooseTarget(myShots), enemyFleet, myShots);
                Delay.millis(450);
                if (!gameOver) {
                    computerShot(myFleet, enemyShots);
                    Delay.millis(450);
                }
                continue;
            }
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            waitForRelease();

            if (y < HEADER_HEIGHT) {
                Controls.autopilot = true;
                showStatus("CPU commander", TftTouchShield.CYAN);
                continue;
            }

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

    static void newGame(byte[] myFleet, byte[] enemyFleet, byte[] myShots, byte[] enemyShots) {
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

    static void playerShot(int cell, byte[] enemyFleet, byte[] myShots) {
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

    static void computerShot(byte[] myFleet, byte[] enemyShots) {
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
}
