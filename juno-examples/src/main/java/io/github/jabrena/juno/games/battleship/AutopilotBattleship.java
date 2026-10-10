package io.github.jabrena.juno.games.battleship;

import io.github.jabrena.juno.api.Random;

import static io.github.jabrena.juno.games.battleship.AutopilotBattleship.*;
import static io.github.jabrena.juno.games.battleship.Battleship.*;
import static io.github.jabrena.juno.games.battleship.Controls.*;
import static io.github.jabrena.juno.games.battleship.Fleet.*;
import static io.github.jabrena.juno.games.battleship.SceneRenderer.*;

final class AutopilotBattleship {
    private AutopilotBattleship() {
    }

    static int chooseTarget(byte[] shots) {
        int target = lineTarget(shots);
        if (target >= 0) {
            return target;
        }
        target = adjacentHitTarget(shots);
        return target >= 0 ? target : huntTarget(shots);
    }

    private static int lineTarget(byte[] shots) {
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
        return -1;
    }

    private static int adjacentHitTarget(byte[] shots) {
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
        return -1;
    }

    private static int huntTarget(byte[] shots) {
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

    static int neighbour(int cell, int direction) {
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
}
