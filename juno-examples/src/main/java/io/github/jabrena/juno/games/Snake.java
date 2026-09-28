package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Snake on the ELEGOO 2.8" TFT touch screen shield. The snake heads for the food; each bite makes
 * it one cell longer and a little faster. Running into a wall or into itself ends the game.
 *
 * <p>By default the CPU plays by itself ({@code CPU} in the header) and starts a new game after
 * each crash. Tap the header to take over ({@code YOU}) and back. When you play, steering is
 * relative to the snake's head: while it moves sideways, tap above or below the head to turn up or
 * down; while it moves vertically, tap to the left or right of the head. Tap to restart.
 *
 * <p>The CPU finds the shortest path to the food with a breadth-first search, but only takes its
 * first step when a flood fill from the new head still reaches at least as many free cells as the
 * snake is long — otherwise it could trap itself in its own coils. When the food is unreachable or
 * unsafe, it takes the move that keeps the most open space. The cell the tail is about to leave
 * counts as free.
 *
 * <p>Only what changes is drawn each step — the new head, the previous head (now body) and the
 * erased tail — so the speed does not depend on the snake's length. The body is a ring buffer of
 * cell indices plus an occupancy map for collision checks.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Snake {
    private static final int COLUMNS = 20;
    private static final int ROWS = 23;
    private static final int CELLS = COLUMNS * ROWS;
    private static final int CELL = 12;
    private static final int FIELD_Y = 36;
    private static final int START_LENGTH = 4;
    private static final int START_INTERVAL = 180;
    private static final int MIN_INTERVAL = 70;

    private static final int UP = 0;
    private static final int RIGHT = 1;
    private static final int DOWN = 2;
    private static final int LEFT = 3;

    private static final int FIELD = 0x0120;
    private static final int BODY = 0x07E0;
    private static final int HEAD = 0xAFE5;
    private static final int FOOD = TftTouchShield.RED;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static int head;
    private static int tail;
    private static int length;
    private static int direction;
    private static int nextDirection;
    private static int food;
    private static int score;
    private static int best;
    private static int interval;
    private static boolean touching;
    private static int releaseMisses;
    private static boolean autopilot;

    private Snake() {
    }

    public static void main(String[] args) {
        int[] body = new int[CELLS];
        byte[] occupied = new byte[CELLS];
        short[] queue = new short[CELLS];
        byte[] mark = new byte[CELLS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        Random.seed(Clock.micros());
        autopilot = true;

        while (true) {
            newGame(body, occupied);
            int lastStep = Clock.millis();
            boolean alive = true;
            while (alive) {
                pollTouch(body);
                if (Clock.millis() - lastStep >= interval) {
                    lastStep = Clock.millis();
                    if (autopilot) {
                        nextDirection = chooseDirection(body, occupied, queue, mark);
                    }
                    alive = step(body, occupied);
                }
            }
            if (score > best) {
                best = score;
            }
            if (autopilot) {
                showStatus("CPU crashed", TftTouchShield.RED);
                Delay.millis(1500);
            } else {
                showStatus("Game over - tap", TftTouchShield.RED);
                Delay.millis(600);
                waitForTap();
            }
        }
    }

    // ---- Game flow ----

    private static void newGame(int[] body, byte[] occupied) {
        for (int i = 0; i < CELLS; i++) {
            occupied[i] = 0;
        }
        TftTouchShield.fillRect(0, FIELD_Y, COLUMNS * CELL, ROWS * CELL, FIELD);
        int row = ROWS / 2;
        tail = 0;
        length = START_LENGTH;
        for (int i = 0; i < START_LENGTH; i++) {
            int cell = row * COLUMNS + 4 + i;
            body[i] = cell;
            occupied[cell] = 1;
            drawCell(cell, BODY);
        }
        head = START_LENGTH - 1;
        drawCell(body[head], HEAD);
        direction = RIGHT;
        nextDirection = RIGHT;
        score = 0;
        interval = START_INTERVAL;
        placeFood(occupied);
        drawScore();
    }

    /** Advances one cell; returns false when the snake crashes. */
    private static boolean step(int[] body, byte[] occupied) {
        direction = nextDirection;
        int current = body[head];
        int row = current / COLUMNS;
        int column = current % COLUMNS;
        if (direction == UP) {
            row = row - 1;
        } else if (direction == DOWN) {
            row = row + 1;
        } else if (direction == LEFT) {
            column = column - 1;
        } else {
            column = column + 1;
        }
        if (row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
            return false;
        }
        int next = row * COLUMNS + column;
        boolean eating = next == food;
        if (!eating) {
            // The tail moves away this step, so the head may enter the cell it leaves.
            int tailCell = body[tail];
            occupied[tailCell] = 0;
            drawCell(tailCell, FIELD);
            tail = (tail + 1) % CELLS;
            length = length - 1;
        }
        if (occupied[next] != 0) {
            return false;
        }
        drawCell(current, BODY);
        head = (head + 1) % CELLS;
        body[head] = next;
        occupied[next] = 1;
        length = length + 1;
        drawCell(next, HEAD);
        if (eating) {
            score = score + 1;
            interval = Math.max(MIN_INTERVAL, interval - 5);
            drawScore();
            if (length == CELLS) {
                return false;
            }
            placeFood(occupied);
        }
        return true;
    }

    private static void placeFood(byte[] occupied) {
        int free = CELLS - length;
        int pick = Random.nextInt(free);
        for (int cell = 0; cell < CELLS; cell++) {
            if (occupied[cell] == 0) {
                if (pick == 0) {
                    food = cell;
                    int x = (cell % COLUMNS) * CELL + CELL / 2;
                    int y = FIELD_Y + (cell / COLUMNS) * CELL + CELL / 2;
                    TftTouchShield.fillCircle(x, y, 4, FOOD);
                    return;
                }
                pick = pick - 1;
            }
        }
    }

    // ---- CPU ----

    /** The CPU's next direction: towards the food when that is safe, else towards the most room. */
    private static int chooseDirection(int[] body, byte[] occupied, short[] queue, byte[] mark) {
        int start = body[head];
        int tailCell = body[tail];
        int towardsFood = firstStepTowards(start, food, tailCell, occupied, queue, mark);
        if (towardsFood >= 0 && room(neighbourCell(start, towardsFood), tailCell, occupied, queue, mark,
                length) >= length) {
            return towardsFood;
        }
        int bestDirection = direction;
        int bestRoom = -1;
        for (int d = 0; d < 4; d++) {
            int next = neighbourCell(start, d);
            if (!passable(next, tailCell, occupied)) {
                continue;
            }
            int space = room(next, tailCell, occupied, queue, mark, CELLS);
            if (space > bestRoom) {
                bestRoom = space;
                bestDirection = d;
            }
        }
        return bestDirection;
    }

    /**
     * Breadth-first search from {@code start} to {@code target}; returns the direction of the first
     * step on a shortest path, or -1 when the target is unreachable. {@code mark} holds, for each
     * visited cell, that first direction + 1.
     */
    private static int firstStepTowards(int start, int target, int tailCell, byte[] occupied, short[] queue,
            byte[] mark) {
        for (int i = 0; i < CELLS; i++) {
            mark[i] = 0;
        }
        mark[start] = 5;
        int read = 0;
        int write = 0;
        for (int d = 0; d < 4; d++) {
            int next = neighbourCell(start, d);
            if (passable(next, tailCell, occupied) && mark[next] == 0) {
                if (next == target) {
                    return d;
                }
                mark[next] = (byte) (d + 1);
                queue[write] = (short) next;
                write = write + 1;
            }
        }
        while (read < write) {
            int cell = queue[read];
            read = read + 1;
            for (int d = 0; d < 4; d++) {
                int next = neighbourCell(cell, d);
                if (passable(next, tailCell, occupied) && mark[next] == 0) {
                    if (next == target) {
                        return mark[cell] - 1;
                    }
                    mark[next] = mark[cell];
                    queue[write] = (short) next;
                    write = write + 1;
                }
            }
        }
        return -1;
    }

    /** Counts the free cells reachable from {@code start} (itself included), stopping at {@code limit}. */
    private static int room(int start, int tailCell, byte[] occupied, short[] queue, byte[] mark, int limit) {
        for (int i = 0; i < CELLS; i++) {
            mark[i] = 0;
        }
        mark[start] = 1;
        queue[0] = (short) start;
        int read = 0;
        int write = 1;
        while (read < write && write < limit) {
            int cell = queue[read];
            read = read + 1;
            for (int d = 0; d < 4; d++) {
                int next = neighbourCell(cell, d);
                if (passable(next, tailCell, occupied) && mark[next] == 0) {
                    mark[next] = 1;
                    queue[write] = (short) next;
                    write = write + 1;
                }
            }
        }
        return write;
    }

    private static boolean passable(int cell, int tailCell, byte[] occupied) {
        return cell >= 0 && (occupied[cell] == 0 || cell == tailCell);
    }

    /** The adjacent cell in {@code direction}, or -1 past a wall. */
    private static int neighbourCell(int cell, int direction) {
        int row = cell / COLUMNS;
        int column = cell % COLUMNS;
        if (direction == UP) {
            row = row - 1;
        } else if (direction == DOWN) {
            row = row + 1;
        } else if (direction == LEFT) {
            column = column - 1;
        } else {
            column = column + 1;
        }
        if (row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
            return -1;
        }
        return row * COLUMNS + column;
    }

    // ---- Input ----

    /** Turns on each new press, relative to the head; holding the touch does not repeat. */
    private static void pollTouch(int[] body) {
        if (!TftTouchShield.readTouch()) {
            releaseMisses = releaseMisses + 1;
            if (releaseMisses >= 3) {
                touching = false;
            }
            return;
        }
        releaseMisses = 0;
        if (touching) {
            return;
        }
        touching = true;
        if (TftTouchShield.touchY() < FIELD_Y) {
            autopilot = !autopilot;
            drawScore();
            return;
        }
        if (autopilot) {
            return;
        }
        int cell = body[head];
        int dx = TftTouchShield.touchX() - ((cell % COLUMNS) * CELL + CELL / 2);
        int dy = TftTouchShield.touchY() - (FIELD_Y + (cell / COLUMNS) * CELL + CELL / 2);
        if (direction == LEFT || direction == RIGHT) {
            if (dy < 0) {
                nextDirection = UP;
            } else {
                nextDirection = DOWN;
            }
        } else if (dx < 0) {
            nextDirection = LEFT;
        } else {
            nextDirection = RIGHT;
        }
    }

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
        touching = false;
    }

    // ---- Drawing ----

    private static void drawCell(int cell, int color) {
        TftTouchShield.fillRect((cell % COLUMNS) * CELL, FIELD_Y + (cell / COLUMNS) * CELL, CELL, CELL, FIELD);
        if (color != FIELD) {
            TftTouchShield.fillRect((cell % COLUMNS) * CELL + 1, FIELD_Y + (cell / COLUMNS) * CELL + 1,
                    CELL - 2, CELL - 2, color);
        }
    }

    private static void drawScore() {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), FIELD_Y - 2, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 9);
        if (autopilot) {
            TftTouchShield.setTextColor(TftTouchShield.CYAN, HEADER_BACKGROUND);
            TftTouchShield.print("CPU ");
        } else {
            TftTouchShield.print("YOU ");
        }
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.print(score);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(140, 9);
        TftTouchShield.print("Best ");
        TftTouchShield.print(best);
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), FIELD_Y - 2, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 9);
        TftTouchShield.print(text);
    }
}
