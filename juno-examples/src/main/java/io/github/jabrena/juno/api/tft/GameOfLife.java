package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Conway's Game of Life on the ELEGOO 2.8" TFT touch screen shield: a 40x46 grid whose edges wrap
 * around (a torus). A live cell with two or three live neighbours survives, a dead cell with exactly
 * three comes alive, and every other cell dies or stays dead.
 *
 * <p>Buttons: {@code RUN}/{@code STOP} toggles the simulation, {@code STEP} advances one
 * generation, {@code RAND} fills the grid with random cells and {@code CLR} empties it. While
 * stopped, drag on the grid to draw live cells; a stroke that starts on a live cell erases instead.
 * The header shows the generation and the population.
 *
 * <p>Two grids alternate as the current and the next generation, and only cells whose state
 * changed are redrawn, so quiet patterns run faster than busy ones.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class GameOfLife {
    private static final int COLUMNS = 40;
    private static final int ROWS = 46;
    private static final int CELLS = COLUMNS * ROWS;
    private static final int RANDOM_PERCENT = 28;

    // Layout (portrait, 240x320).
    private static final int HEADER_HEIGHT = 20;
    private static final int CELL = 6;
    private static final int GRID_Y = 22;
    private static final int BUTTON_Y = GRID_Y + ROWS * CELL + 2;
    private static final int BUTTON_WIDTH = 58;
    private static final int BUTTON_SPACING = 60;
    private static final int BUTTON_HEIGHT = 320 - BUTTON_Y;

    // Buttons.
    private static final int RUN = 0;
    private static final int STEP = 1;
    private static final int RANDOMIZE = 2;
    private static final int CLEAR = 3;

    private static final int ALIVE = 0x07E8;
    private static final int DEAD = TftTouchShield.BLACK;
    private static final int BUTTON_COLOR = 0x4A69;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static boolean running;
    private static int generation;
    private static int population;

    private GameOfLife() {
    }

    public static void main(String[] args) {
        byte[] current = new byte[CELLS];
        byte[] next = new byte[CELLS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(DEAD);
        Random.seed(Clock.micros());
        randomize(current);
        running = true;
        drawButtons();
        drawHeader();

        while (true) {
            if (TftTouchShield.readTouch()) {
                handleTouch(current, next);
            }
            if (running) {
                advance(current, next);
                // Copy back so "current" always names the grid on screen.
                for (int i = 0; i < CELLS; i++) {
                    current[i] = next[i];
                }
                drawHeader();
            } else {
                Delay.millis(10);
            }
        }
    }

    // ---- Simulation ----

    /** Computes the next generation into {@code next}, drawing each cell that changes. */
    private static void advance(byte[] current, byte[] next) {
        population = 0;
        for (int row = 0; row < ROWS; row++) {
            int up = (row + ROWS - 1) % ROWS * COLUMNS;
            int middle = row * COLUMNS;
            int down = (row + 1) % ROWS * COLUMNS;
            for (int column = 0; column < COLUMNS; column++) {
                int left = (column + COLUMNS - 1) % COLUMNS;
                int right = (column + 1) % COLUMNS;
                int neighbours = current[up + left] + current[up + column] + current[up + right]
                        + current[middle + left] + current[middle + right]
                        + current[down + left] + current[down + column] + current[down + right];
                int cell = middle + column;
                int alive = 0;
                if (neighbours == 3 || (neighbours == 2 && current[cell] != 0)) {
                    alive = 1;
                    population = population + 1;
                }
                next[cell] = (byte) alive;
                if (alive != current[cell]) {
                    drawCell(cell, alive != 0);
                }
            }
        }
        generation = generation + 1;
    }

    private static void randomize(byte[] grid) {
        population = 0;
        for (int i = 0; i < CELLS; i++) {
            int alive = 0;
            if (Random.nextInt(100) < RANDOM_PERCENT) {
                alive = 1;
                population = population + 1;
            }
            if (alive != grid[i]) {
                grid[i] = (byte) alive;
                drawCell(i, alive != 0);
            }
        }
        generation = 0;
    }

    private static void clear(byte[] grid) {
        for (int i = 0; i < CELLS; i++) {
            if (grid[i] != 0) {
                grid[i] = 0;
                drawCell(i, false);
            }
        }
        population = 0;
        generation = 0;
    }

    // ---- Input ----

    private static void handleTouch(byte[] grid, byte[] next) {
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        if (y >= BUTTON_Y) {
            int button = x / BUTTON_SPACING;
            waitForRelease();
            if (button == RUN) {
                running = !running;
                drawButtons();
            } else if (button == STEP) {
                running = false;
                drawButtons();
                advance(grid, next);
                for (int i = 0; i < CELLS; i++) {
                    grid[i] = next[i];
                }
            } else if (button == RANDOMIZE) {
                randomize(grid);
            } else if (button == CLEAR) {
                running = false;
                drawButtons();
                clear(grid);
            }
            drawHeader();
            return;
        }
        if (running || y < GRID_Y) {
            return;
        }
        paint(grid, x, y);
    }

    /** Draws (or, when the stroke starts on a live cell, erases) cells under the finger until release. */
    private static void paint(byte[] grid, int x, int y) {
        int first = cellAt(x, y);
        if (first < 0) {
            return;
        }
        int value = 1;
        if (grid[first] != 0) {
            value = 0;
        }
        int misses = 0;
        while (misses < 3) {
            int cell = cellAt(x, y);
            if (cell >= 0 && grid[cell] != value) {
                grid[cell] = (byte) value;
                population = population + 2 * value - 1;
                drawCell(cell, value != 0);
            }
            Delay.millis(5);
            if (TftTouchShield.readTouch()) {
                x = TftTouchShield.touchX();
                y = TftTouchShield.touchY();
                misses = 0;
            } else {
                misses = misses + 1;
            }
        }
        generation = 0;
        drawHeader();
    }

    private static int cellAt(int x, int y) {
        if (y < GRID_Y || y >= GRID_Y + ROWS * CELL || x < 0 || x >= COLUMNS * CELL) {
            return -1;
        }
        return ((y - GRID_Y) / CELL) * COLUMNS + x / CELL;
    }

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

    private static void drawCell(int cell, boolean alive) {
        int color = DEAD;
        if (alive) {
            color = ALIVE;
        }
        TftTouchShield.fillRect((cell % COLUMNS) * CELL, GRID_Y + (cell / COLUMNS) * CELL, CELL - 1, CELL - 1, color);
    }

    private static void drawHeader() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("Gen ");
        TftTouchShield.print(generation);
        TftTouchShield.print("   ");
        TftTouchShield.setTextColor(ALIVE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(132, 2);
        TftTouchShield.print("Pop ");
        TftTouchShield.print(population);
        TftTouchShield.print("   ");
    }

    private static void drawButtons() {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        if (running) {
            drawButton(RUN, "STOP");
        } else {
            drawButton(RUN, "RUN");
        }
        drawButton(STEP, "STEP");
        drawButton(RANDOMIZE, "RAND");
        drawButton(CLEAR, "CLR");
    }

    private static void drawButton(int index, String label) {
        int x = index * BUTTON_SPACING;
        TftTouchShield.fillRect(x, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_COLOR);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BUTTON_COLOR);
        TftTouchShield.setCursor(x + (BUTTON_WIDTH - label.length() * 12) / 2, BUTTON_Y + (BUTTON_HEIGHT - 14) / 2);
        TftTouchShield.print(label);
    }
}
