package io.github.jabrena.juno.games.battleship;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;

import static io.github.jabrena.juno.games.battleship.AutopilotBattleship.chooseTarget;
import static io.github.jabrena.juno.games.battleship.Controls.waitForRelease;
import static io.github.jabrena.juno.games.battleship.Fleet.fleetDestroyed;
import static io.github.jabrena.juno.games.battleship.Fleet.isSunk;
import static io.github.jabrena.juno.games.battleship.Fleet.markSunk;
import static io.github.jabrena.juno.games.battleship.Fleet.placeFleet;
import static io.github.jabrena.juno.games.battleship.Fleet.shipName;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.drawEnemyCell;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.drawMyCell;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.drawShipsLeft;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.drawShipsLeftCounts;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.showStatus;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.showStatus2;

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
@Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
public final class Battleship {
    private static final Clock CLOCK = ArduinoUno.getClock();
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
        TftTouchShield tft = new TftTouchShield();
        byte[] myFleet = new byte[CELLS];
        byte[] enemyFleet = new byte[CELLS];
        byte[] myShots = new byte[CELLS];
        byte[] enemyShots = new byte[CELLS];

        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.cover(tft);
                tapped = Controls.waitForTap(tft, COVER_TIMEOUT_MILLIS);
            }
            Controls.chooseCommander(tft);
            newGame(tft, myFleet, enemyFleet, myShots, enemyShots);
            playGame(tft, myFleet, enemyFleet, myShots, enemyShots);
            Delay.millis(3000);
        }
    }

    private static void playGame(TftTouchShield tft, byte[] myFleet, byte[] enemyFleet, byte[] myShots, byte[] enemyShots) {
        while (!gameOver) {
            if (Controls.autopilot) {
                playerShot(tft, chooseTarget(myShots), enemyFleet, myShots);
                Delay.millis(450);
                if (!gameOver) {
                    computerShot(tft, myFleet, enemyShots);
                    Delay.millis(450);
                }
                continue;
            }
            if (!tft.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = tft.touchX();
            int y = tft.touchY();
            waitForRelease(tft);

            if (y < HEADER_HEIGHT) {
                Controls.autopilot = true;
                showStatus(tft, "CPU commander", TftTouchShield.CYAN);
                continue;
            }

            if (x >= NEW_X && y >= NEW_Y) {
                newGame(tft, myFleet, enemyFleet, myShots, enemyShots);
                continue;
            }
            if (gameOver || x < GRID_X || y < GRID_Y || x >= GRID_X + SIZE * CELL || y >= GRID_Y + SIZE * CELL) {
                continue;
            }
            int cell = ((y - GRID_Y) / CELL) * SIZE + (x - GRID_X) / CELL;
            if (myShots[cell] != UNKNOWN) {
                continue;
            }
            playerShot(tft, cell, enemyFleet, myShots);
            if (gameOver) {
                continue;
            }
            Delay.millis(500);
            computerShot(tft, myFleet, enemyShots);
        }
    }

    // ---- Game flow ----

    static void newGame(TftTouchShield tft, byte[] myFleet, byte[] enemyFleet, byte[] myShots, byte[] enemyShots) {
        if (!seeded) {
            Random.seed(CLOCK.micros());
            seeded = true;
        }
        placeFleet(myFleet);
        placeFleet(enemyFleet);
        for (int i = 0; i < CELLS; i++) {
            myShots[i] = UNKNOWN;
            enemyShots[i] = UNKNOWN;
        }
        gameOver = false;

        tft.fillScreen(TftTouchShield.BLACK);
        for (int i = 0; i < CELLS; i++) {
            drawEnemyCell(tft, i, UNKNOWN);
            drawMyCell(tft, i, myFleet[i], UNKNOWN);
        }
        tft.fillRect(NEW_X, NEW_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        tft.setCursor(NEW_X + 12, NEW_Y + 7);
        tft.print("NEW");
        drawShipsLeft(tft, myFleet, enemyFleet, myShots, enemyShots);
        showStatus(tft, "Fire at will!", TftTouchShield.WHITE);
    }

    static void playerShot(TftTouchShield tft, int cell, byte[] enemyFleet, byte[] myShots) {
        int ship = enemyFleet[cell];
        if (ship == 0) {
            myShots[cell] = MISS;
            drawEnemyCell(tft, cell, MISS);
            showStatus(tft, "Miss", TftTouchShield.GRAY);
            return;
        }
        myShots[cell] = HIT;
        drawEnemyCell(tft, cell, HIT);
        if (!isSunk(enemyFleet, myShots, ship)) {
            showStatus(tft, "Hit!", TftTouchShield.RED);
            return;
        }
        markSunk(enemyFleet, myShots, ship);
        for (int i = 0; i < CELLS; i++) {
            if (enemyFleet[i] == ship) {
                drawEnemyCell(tft, i, SUNK);
            }
        }
        showStatus2(tft, "You sank ", shipName(ship), TftTouchShield.YELLOW);
        drawShipsLeftCounts(tft, enemyFleet, myShots, false);
        if (fleetDestroyed(enemyFleet, myShots)) {
            gameOver = true;
            showStatus(tft, "You win!", TftTouchShield.GREEN);
        }
    }

    static void computerShot(TftTouchShield tft, byte[] myFleet, byte[] enemyShots) {
        int cell = chooseTarget(enemyShots);
        int ship = myFleet[cell];
        if (ship == 0) {
            enemyShots[cell] = MISS;
            drawMyCell(tft, cell, 0, MISS);
        } else {
            enemyShots[cell] = HIT;
            drawMyCell(tft, cell, ship, HIT);
            if (isSunk(myFleet, enemyShots, ship)) {
                markSunk(myFleet, enemyShots, ship);
                showStatus2(tft, "CPU sank ", shipName(ship), TftTouchShield.RED);
                if (fleetDestroyed(myFleet, enemyShots)) {
                    gameOver = true;
                    showStatus(tft, "Computer wins", TftTouchShield.RED);
                }
            }
        }
        drawShipsLeftCounts(tft, myFleet, enemyShots, true);
    }
}
