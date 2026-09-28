package io.github.jabrena.juno.api.led;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;

/**
 * A self-playing Snake on the UNO R4 WiFi's 12x8 LED matrix. The body is a fixed-size
 * {@code int[]} shift register, always shifted by one cell per tick; {@code length} controls how
 * many of those leading cells are actually lit, which is what makes the snake grow each time it eats
 * food. Each cell is a single packed {@code y * WIDTH + x} position (matching the LED matrix's own
 * bit index) instead of an (x, y) pair.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class LedMatrixSnake {
    private static final int WIDTH = 12;
    private static final int HEIGHT = 8;
    private static final int MAX_LENGTH = 16;
    private static final int NO_DIR = -1;

    // Direction codes: 0 = up, 1 = right, 2 = down, 3 = left.
    private static final int UP = 0;
    private static final int RIGHT = 1;
    private static final int DOWN = 2;
    private static final int LEFT = 3;

    public static void main(String[] args) {
        // body[0] is the segment right behind the head; body[MAX_LENGTH - 1] is the tail end.
        int[] body = new int[MAX_LENGTH];
        int headPos = packPos(6, 4);
        int dir = RIGHT;
        int length = 2;
        resetBody(body);
        int foodPos = packPos(9, 2);
        int rngState = 12345;

        LedMatrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];

        while (true) {
            int chosenDir = chooseDir(dir, headPos, foodPos, length, body);
            if (chosenDir == NO_DIR) {
                // Boxed in by its own body: restart the game.
                headPos = packPos(6, 4);
                dir = RIGHT;
                length = 2;
                resetBody(body);
                foodPos = packPos(9, 2);
                chosenDir = dir;
            }

            for (int i = MAX_LENGTH - 1; i > 0; i--) {
                body[i] = body[i - 1];
            }
            body[0] = headPos;
            headPos = packPos(posX(headPos) + dirDx(chosenDir), posY(headPos) + dirDy(chosenDir));
            dir = chosenDir;

            if (headPos == foodPos) {
                if (length < MAX_LENGTH) {
                    length = length + 1;
                }
                int attempt = 0;
                while (attempt < 4) {
                    rngState = rngState * 1103515245 + 12345;
                    int candidateX = rawMod(rngState, WIDTH);
                    rngState = rngState * 1103515245 + 12345;
                    int candidateY = rawMod(rngState, HEIGHT);
                    int candidatePos = packPos(candidateX, candidateY);
                    if (spawnConflicts(candidatePos, headPos, length, body)) {
                        attempt = attempt + 1;
                    } else {
                        foodPos = candidatePos;
                        attempt = 4;
                    }
                }
            }

            LedCanvas.clear(frame);
            LedCanvas.setPixel(frame, posX(headPos), posY(headPos));
            for (int i = 0; i < length; i++) {
                LedCanvas.setPixel(frame, posX(body[i]), posY(body[i]));
            }
            LedCanvas.setPixel(frame, posX(foodPos), posY(foodPos));

            LedCanvas.show(frame);
            Delay.millis(220);
        }
    }

    /** The starting snake: a horizontal line trailing left from the head at (6, 4). */
    private static void resetBody(int[] body) {
        for (int i = 0; i < MAX_LENGTH; i++) {
            int x = 5 - i;
            if (x < 0) {
                x = 0;
            }
            body[i] = packPos(x, 4);
        }
    }

    /**
     * Greedily heads toward the food: horizontally first, then vertically, then keeps going
     * straight, then turns clockwise, counterclockwise, and finally reverses — taking the first of
     * those that stays on the board without hitting the body. Returns {@link #NO_DIR} if none does.
     */
    private static int chooseDir(int dir, int headPos, int foodPos, int length, int[] body) {
        int chosenDir = preferDir(NO_DIR, horizontalPreference(headPos, foodPos), headPos, length, body);
        chosenDir = preferDir(chosenDir, verticalPreference(headPos, foodPos), headPos, length, body);
        chosenDir = preferDir(chosenDir, dir, headPos, length, body);
        chosenDir = preferDir(chosenDir, (dir + 1) % 4, headPos, length, body);
        chosenDir = preferDir(chosenDir, (dir + 3) % 4, headPos, length, body);
        return preferDir(chosenDir, (dir + 2) % 4, headPos, length, body);
    }

    /** Keeps {@code chosenDir} once one was found; otherwise takes {@code candidate} if it is valid. */
    private static int preferDir(int chosenDir, int candidate, int headPos, int length, int[] body) {
        if (chosenDir != NO_DIR || candidate == NO_DIR) {
            return chosenDir;
        }
        if (isValidDir(candidate, headPos, length, body)) {
            return candidate;
        }
        return NO_DIR;
    }

    private static int horizontalPreference(int headPos, int foodPos) {
        if (posX(foodPos) > posX(headPos)) {
            return RIGHT;
        }
        if (posX(foodPos) < posX(headPos)) {
            return LEFT;
        }
        return NO_DIR;
    }

    private static int verticalPreference(int headPos, int foodPos) {
        if (posY(foodPos) > posY(headPos)) {
            return DOWN;
        }
        if (posY(foodPos) < posY(headPos)) {
            return UP;
        }
        return NO_DIR;
    }

    private static int packPos(int x, int y) {
        return y * WIDTH + x;
    }

    private static int posX(int pos) {
        return pos % WIDTH;
    }

    private static int posY(int pos) {
        return pos / WIDTH;
    }

    private static int dirDx(int dir) {
        if (dir == RIGHT) {
            return 1;
        }
        if (dir == LEFT) {
            return -1;
        }
        return 0;
    }

    private static int dirDy(int dir) {
        if (dir == DOWN) {
            return 1;
        }
        if (dir == UP) {
            return -1;
        }
        return 0;
    }

    private static boolean inBounds(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    private static boolean collidesWithBody(int pos, int length, int[] body) {
        for (int i = 0; i < length; i++) {
            if (pos == body[i]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidDir(int dir, int headPos, int length, int[] body) {
        int nextX = posX(headPos) + dirDx(dir);
        int nextY = posY(headPos) + dirDy(dir);
        if (!inBounds(nextX, nextY)) {
            return false;
        }
        int nextPos = packPos(nextX, nextY);
        return !collidesWithBody(nextPos, length, body);
    }

    private static boolean spawnConflicts(int pos, int headPos, int length, int[] body) {
        if (pos == headPos) {
            return true;
        }
        return collidesWithBody(pos, length, body);
    }

    private static int rawMod(int value, int modulus) {
        int remainder = value % modulus;
        if (remainder < 0) {
            remainder = remainder + modulus;
        }
        return remainder;
    }
}
