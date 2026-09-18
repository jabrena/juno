import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * A self-playing Snake on the UNO R4 WiFi's 12x8 LED matrix. Juno v0.1 has no arrays, objects, or
 * static fields, so the body is a fixed-size shift register of local variables in {@code main},
 * always shifted by one cell per tick; {@code length} controls how many of those trailing cells
 * are actually lit, which is what makes the snake grow each time it eats food. Each cell is a
 * single packed {@code y * WIDTH + x} position (matching the LED matrix's own bit index) instead
 * of an (x, y) pair, so a deeper body only costs one extra local/parameter per segment.
 */
public final class LedMatrixSnake {
    private static final int WIDTH = 12;
    private static final int HEIGHT = 8;
    private static final int MAX_LENGTH = 16;

    // Direction codes: 0 = up, 1 = right, 2 = down, 3 = left.
    private static final int UP = 0;
    private static final int RIGHT = 1;
    private static final int DOWN = 2;
    private static final int LEFT = 3;

    public static void main(String[] args) {
        int headPos = packPos(6, 4);
        int dir = RIGHT;
        int length = 2;
        int s1 = packPos(5, 4);
        int s2 = packPos(4, 4);
        int s3 = packPos(3, 4);
        int s4 = packPos(2, 4);
        int s5 = packPos(1, 4);
        int s6 = packPos(0, 4);
        int s7 = s6;
        int s8 = s6;
        int s9 = s6;
        int s10 = s6;
        int s11 = s6;
        int s12 = s6;
        int s13 = s6;
        int s14 = s6;
        int s15 = s6;
        int s16 = s6;
        int foodPos = packPos(9, 2);
        int rngState = 12345;

        LedMatrix.begin();

        while (true) {
            int headX = posX(headPos);
            int headY = posY(headPos);
            int foodX = posX(foodPos);
            int foodY = posY(foodPos);

            int horizontalPreference = -1;
            if (foodX > headX) {
                horizontalPreference = RIGHT;
            } else if (foodX < headX) {
                horizontalPreference = LEFT;
            }
            int verticalPreference = -1;
            if (foodY > headY) {
                verticalPreference = DOWN;
            } else if (foodY < headY) {
                verticalPreference = UP;
            }
            int clockwise = (dir + 1) % 4;
            int counterClockwise = (dir + 3) % 4;
            int reverse = (dir + 2) % 4;

            int chosenDir = -1;
            if (chosenDir == -1 && horizontalPreference != -1
                    && isValidDir(horizontalPreference, headPos, length,
                            s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16)) {
                chosenDir = horizontalPreference;
            }
            if (chosenDir == -1 && verticalPreference != -1
                    && isValidDir(verticalPreference, headPos, length,
                            s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16)) {
                chosenDir = verticalPreference;
            }
            if (chosenDir == -1 && isValidDir(dir, headPos, length,
                    s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16)) {
                chosenDir = dir;
            }
            if (chosenDir == -1 && isValidDir(clockwise, headPos, length,
                    s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16)) {
                chosenDir = clockwise;
            }
            if (chosenDir == -1 && isValidDir(counterClockwise, headPos, length,
                    s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16)) {
                chosenDir = counterClockwise;
            }
            if (chosenDir == -1 && isValidDir(reverse, headPos, length,
                    s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16)) {
                chosenDir = reverse;
            }

            if (chosenDir == -1) {
                // Boxed in by its own body: restart the game.
                headPos = packPos(6, 4);
                dir = RIGHT;
                length = 2;
                s1 = packPos(5, 4);
                s2 = packPos(4, 4);
                s3 = packPos(3, 4);
                s4 = packPos(2, 4);
                s5 = packPos(1, 4);
                s6 = packPos(0, 4);
                s7 = s6;
                s8 = s6;
                s9 = s6;
                s10 = s6;
                s11 = s6;
                s12 = s6;
                s13 = s6;
                s14 = s6;
                s15 = s6;
                s16 = s6;
                foodPos = packPos(9, 2);
                chosenDir = dir;
            }

            s16 = s15;
            s15 = s14;
            s14 = s13;
            s13 = s12;
            s12 = s11;
            s11 = s10;
            s10 = s9;
            s9 = s8;
            s8 = s7;
            s7 = s6;
            s6 = s5;
            s5 = s4;
            s4 = s3;
            s3 = s2;
            s2 = s1;
            s1 = headPos;
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
                    if (spawnConflicts(candidatePos, headPos, length,
                            s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16)) {
                        attempt = attempt + 1;
                    } else {
                        foodPos = candidatePos;
                        attempt = 4;
                    }
                }
            }

            int word0 = 0;
            int word1 = 0;
            int word2 = 0;
            word0 = setPixel(word0, 0, headPos);
            word1 = setPixel(word1, 1, headPos);
            word2 = setPixel(word2, 2, headPos);
            if (length >= 1) {
                word0 = setPixel(word0, 0, s1);
                word1 = setPixel(word1, 1, s1);
                word2 = setPixel(word2, 2, s1);
            }
            if (length >= 2) {
                word0 = setPixel(word0, 0, s2);
                word1 = setPixel(word1, 1, s2);
                word2 = setPixel(word2, 2, s2);
            }
            if (length >= 3) {
                word0 = setPixel(word0, 0, s3);
                word1 = setPixel(word1, 1, s3);
                word2 = setPixel(word2, 2, s3);
            }
            if (length >= 4) {
                word0 = setPixel(word0, 0, s4);
                word1 = setPixel(word1, 1, s4);
                word2 = setPixel(word2, 2, s4);
            }
            if (length >= 5) {
                word0 = setPixel(word0, 0, s5);
                word1 = setPixel(word1, 1, s5);
                word2 = setPixel(word2, 2, s5);
            }
            if (length >= 6) {
                word0 = setPixel(word0, 0, s6);
                word1 = setPixel(word1, 1, s6);
                word2 = setPixel(word2, 2, s6);
            }
            if (length >= 7) {
                word0 = setPixel(word0, 0, s7);
                word1 = setPixel(word1, 1, s7);
                word2 = setPixel(word2, 2, s7);
            }
            if (length >= 8) {
                word0 = setPixel(word0, 0, s8);
                word1 = setPixel(word1, 1, s8);
                word2 = setPixel(word2, 2, s8);
            }
            if (length >= 9) {
                word0 = setPixel(word0, 0, s9);
                word1 = setPixel(word1, 1, s9);
                word2 = setPixel(word2, 2, s9);
            }
            if (length >= 10) {
                word0 = setPixel(word0, 0, s10);
                word1 = setPixel(word1, 1, s10);
                word2 = setPixel(word2, 2, s10);
            }
            if (length >= 11) {
                word0 = setPixel(word0, 0, s11);
                word1 = setPixel(word1, 1, s11);
                word2 = setPixel(word2, 2, s11);
            }
            if (length >= 12) {
                word0 = setPixel(word0, 0, s12);
                word1 = setPixel(word1, 1, s12);
                word2 = setPixel(word2, 2, s12);
            }
            if (length >= 13) {
                word0 = setPixel(word0, 0, s13);
                word1 = setPixel(word1, 1, s13);
                word2 = setPixel(word2, 2, s13);
            }
            if (length >= 14) {
                word0 = setPixel(word0, 0, s14);
                word1 = setPixel(word1, 1, s14);
                word2 = setPixel(word2, 2, s14);
            }
            if (length >= 15) {
                word0 = setPixel(word0, 0, s15);
                word1 = setPixel(word1, 1, s15);
                word2 = setPixel(word2, 2, s15);
            }
            if (length >= 16) {
                word0 = setPixel(word0, 0, s16);
                word1 = setPixel(word1, 1, s16);
                word2 = setPixel(word2, 2, s16);
            }
            word0 = setPixel(word0, 0, foodPos);
            word1 = setPixel(word1, 1, foodPos);
            word2 = setPixel(word2, 2, foodPos);

            LedMatrix.loadFrame(word0, word1, word2);
            Delay.millis(220);
        }
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

    private static boolean collidesWithBody(int pos, int length,
            int s1, int s2, int s3, int s4, int s5, int s6, int s7, int s8,
            int s9, int s10, int s11, int s12, int s13, int s14, int s15, int s16) {
        if (length >= 1 && pos == s1) {
            return true;
        }
        if (length >= 2 && pos == s2) {
            return true;
        }
        if (length >= 3 && pos == s3) {
            return true;
        }
        if (length >= 4 && pos == s4) {
            return true;
        }
        if (length >= 5 && pos == s5) {
            return true;
        }
        if (length >= 6 && pos == s6) {
            return true;
        }
        if (length >= 7 && pos == s7) {
            return true;
        }
        if (length >= 8 && pos == s8) {
            return true;
        }
        if (length >= 9 && pos == s9) {
            return true;
        }
        if (length >= 10 && pos == s10) {
            return true;
        }
        if (length >= 11 && pos == s11) {
            return true;
        }
        if (length >= 12 && pos == s12) {
            return true;
        }
        if (length >= 13 && pos == s13) {
            return true;
        }
        if (length >= 14 && pos == s14) {
            return true;
        }
        if (length >= 15 && pos == s15) {
            return true;
        }
        return length >= 16 && pos == s16;
    }

    private static boolean isValidDir(int dir, int headPos, int length,
            int s1, int s2, int s3, int s4, int s5, int s6, int s7, int s8,
            int s9, int s10, int s11, int s12, int s13, int s14, int s15, int s16) {
        int nextX = posX(headPos) + dirDx(dir);
        int nextY = posY(headPos) + dirDy(dir);
        if (!inBounds(nextX, nextY)) {
            return false;
        }
        int nextPos = packPos(nextX, nextY);
        return !collidesWithBody(nextPos, length,
                s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16);
    }

    private static boolean spawnConflicts(int pos, int headPos, int length,
            int s1, int s2, int s3, int s4, int s5, int s6, int s7, int s8,
            int s9, int s10, int s11, int s12, int s13, int s14, int s15, int s16) {
        if (pos == headPos) {
            return true;
        }
        return collidesWithBody(pos, length, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16);
    }

    private static int rawMod(int value, int modulus) {
        int remainder = value % modulus;
        if (remainder < 0) {
            remainder = remainder + modulus;
        }
        return remainder;
    }

    private static int setPixel(int word, int wordIndex, int pos) {
        int targetWord = pos / 32;
        if (targetWord != wordIndex) {
            return word;
        }
        int bitFromTop = pos - targetWord * 32;
        int shift = 31 - bitFromTop;
        return word | (1 << shift);
    }
}
