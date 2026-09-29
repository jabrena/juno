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

final class Fleet {
    private Fleet() {
    }

    static int shipLength(int ship) {
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

    static String shipName(int ship) {
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

    static void placeFleet(byte[] fleet) {
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

    static boolean isSunk(byte[] fleet, byte[] shots, int ship) {
        for (int i = 0; i < CELLS; i++) {
            if (fleet[i] == ship && shots[i] < HIT) {
                return false;
            }
        }
        return true;
    }

    static void markSunk(byte[] fleet, byte[] shots, int ship) {
        for (int i = 0; i < CELLS; i++) {
            if (fleet[i] == ship) {
                shots[i] = SUNK;
            }
        }
    }

    static boolean fleetDestroyed(byte[] fleet, byte[] shots) {
        for (int i = 0; i < CELLS; i++) {
            if (fleet[i] != 0 && shots[i] < HIT) {
                return false;
            }
        }
        return true;
    }
}
