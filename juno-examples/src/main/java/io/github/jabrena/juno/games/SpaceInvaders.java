package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Space Invaders on the ELEGOO 2.8" TFT touch screen shield. Touch and hold anywhere: the laser
 * cannon slides towards your finger and fires whenever its single shot is ready. Shoot down all
 * {@value #INVADERS} invaders before they land; the four shields crumble under fire from both
 * sides, and a mystery ship crosses the top of the screen now and then. Squids score 30, crabs 20,
 * octopuses 10, the mystery ship 50 to 300. Three lives; each new wave starts a little lower.
 *
 * <p>Like the arcade machine, the invaders are moved one per frame, bottom row first, so the whole
 * formation ripples across the screen and speeds up as it thins out. Each move streams just the
 * sprite's old-plus-new rectangle with {@code beginPixels}/{@code pushPixel}; the sprites are the
 * classic two-frame bitmaps at double size.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class SpaceInvaders {
    private static final int FRAME_MILLIS = 18;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEIGHT = 320;
    private static final int HEADER = 20;
    private static final int UFO_Y = 24;
    private static final int PLAYER_Y = 280;
    private static final int GROUND_Y = 300;

    // Formation.
    private static final int COLUMNS = 7;
    private static final int ROWS = 5;
    private static final int INVADERS = COLUMNS * ROWS;
    private static final int BOX_WIDTH = 24;
    private static final int BOX_HEIGHT = 16;
    private static final int SPACING_X = 28;
    private static final int SPACING_Y = 22;
    private static final int STEP_X = 3;
    private static final int DROP = 8;
    private static final int START_Y = 56;

    // Sprite kinds.
    private static final int SQUID = 0;
    private static final int CRAB = 1;
    private static final int OCTOPUS = 2;
    private static final int CANNON = 3;
    private static final int UFO = 4;
    private static final int BURST = 5;
    private static final int WRECK = 6;

    private static final int PLAYER_WIDTH = 26;
    private static final int PLAYER_SPEED = 3;
    private static final int UFO_WIDTH = 32;
    private static final int UFO_HEIGHT = 14;

    // Shields: 8x6 blocks of 3x3 pixels, each block taking two hits.
    private static final int SHIELDS = 4;
    private static final int SHIELD_COLUMNS = 8;
    private static final int SHIELD_ROWS = 6;
    private static final int SHIELD_BLOCKS = SHIELD_COLUMNS * SHIELD_ROWS;
    private static final int BLOCK = 3;
    private static final int SHIELD_Y = 240;

    private static final int BULLET_SPEED = 9;
    private static final int BULLET_SUBSTEP = 3;
    private static final int BULLET_HEIGHT = 8;
    private static final int BOMBS = 3;
    private static final int BOMB_HEIGHT = 7;

    private static final int SPACE = TftTouchShield.BLACK;
    private static final int PLAYER_COLOR = 0x07E0;
    private static final int SHIELD_COLOR = 0x07E0;
    private static final int UFO_COLOR = 0xF800;
    private static final int HEADER_BACKGROUND = TftTouchShield.BLACK;

    private static int score;
    private static int best;
    private static int lives;
    private static int wave;
    private static int alive;

    private static int gridX;
    private static int gridY;
    private static int direction;
    private static int frame;
    private static int cursor;
    private static int stepPixels;

    private static int playerX;
    private static int shownPlayerX;
    private static boolean bulletActive;
    private static int bulletX;
    private static int bulletY;

    private static int burstTimer;
    private static int burstX;
    private static int burstY;

    private static int ufoX;
    private static int ufoDirection;
    private static int ufoCountdown;
    private static int shotsFired;

    private static int bombCountdown;

    private SpaceInvaders() {
    }

    public static void main(String[] args) {
        byte[] living = new byte[INVADERS];
        int[] posX = new int[INVADERS];
        int[] posY = new int[INVADERS];
        byte[] shields = new byte[SHIELDS * SHIELD_BLOCKS];
        int[] bombs = new int[BOMBS * 3];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(SPACE);
        drawTitle();
        waitForTap();
        Random.seed(Clock.micros());

        while (true) {
            score = 0;
            lives = 3;
            wave = 0;
            boolean playing = true;
            while (playing) {
                wave = wave + 1;
                startWave(living, posX, posY, shields, bombs);
                playing = playWave(living, posX, posY, shields, bombs);
            }
            if (score > best) {
                best = score;
            }
            drawHeader();
            showCentered("GAME OVER", 130, 3, TftTouchShield.RED);
            showCentered("Tap to play again", 170, 1, TftTouchShield.WHITE);
            Delay.millis(1200);
            waitForTap();
        }
    }

    // ---- Game flow ----

    private static void startWave(byte[] living, int[] posX, int[] posY, byte[] shields, int[] bombs) {
        TftTouchShield.fillScreen(SPACE);
        gridX = (WIDTH - ((COLUMNS - 1) * SPACING_X + BOX_WIDTH)) / 2;
        gridY = START_Y + Math.min(wave - 1, 4) * DROP;
        direction = 1;
        frame = 0;
        cursor = 0;
        stepPixels = STEP_X;
        alive = INVADERS;
        for (int i = 0; i < INVADERS; i++) {
            living[i] = 1;
            posX[i] = gridX + (i % COLUMNS) * SPACING_X;
            posY[i] = gridY + (i / COLUMNS) * SPACING_Y;
            drawSprite(kindOf(i), 0, posX[i], posY[i], posX[i], posY[i], BOX_WIDTH, BOX_HEIGHT, colorOf(i));
        }
        for (int shield = 0; shield < SHIELDS; shield++) {
            for (int block = 0; block < SHIELD_BLOCKS; block++) {
                int value = 2;
                int row = block / SHIELD_COLUMNS;
                int column = block % SHIELD_COLUMNS;
                if (row == 0 && (column == 0 || column == SHIELD_COLUMNS - 1)) {
                    value = 0;
                }
                if (row >= SHIELD_ROWS - 2 && column >= 2 && column <= 5 && !(row == SHIELD_ROWS - 2
                        && (column == 2 || column == 5))) {
                    value = 0;
                }
                shields[shield * SHIELD_BLOCKS + block] = (byte) value;
                drawBlock(shield, block, value);
            }
        }
        for (int i = 0; i < BOMBS * 3; i++) {
            bombs[i] = 0;
        }
        bulletActive = false;
        burstTimer = 0;
        ufoX = -1;
        ufoCountdown = 900;
        bombCountdown = 60;
        TftTouchShield.fillRect(0, GROUND_Y, WIDTH, 1, PLAYER_COLOR);
        spawnPlayer();
        drawHeader();
        drawLives();
    }

    /** Plays until the wave is cleared (true) or the game is over (false). */
    private static boolean playWave(byte[] living, int[] posX, int[] posY, byte[] shields, int[] bombs) {
        int next = Clock.millis();
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;

            movePlayer();
            moveBullet(living, posX, posY, shields);
            if (alive == 0) {
                if (burstTimer > 0) {
                    eraseBurst();
                }
                Delay.millis(800);
                return true;
            }
            if (stepFormation(living, posX, posY, shields)) {
                return false;
            }
            dropBombs(living, posX, posY, bombs);
            if (moveBombs(bombs, shields)) {
                lives = lives - 1;
                drawLives();
                playerDeath(bombs);
                if (lives == 0) {
                    return false;
                }
                spawnPlayer();
                next = Clock.millis();
            }
            moveUfo();
            if (burstTimer > 0) {
                burstTimer = burstTimer - 1;
                if (burstTimer == 0) {
                    eraseBurst();
                }
            }
        }
    }

    private static void spawnPlayer() {
        playerX = (WIDTH - PLAYER_WIDTH) / 2;
        TftTouchShield.fillRect(0, PLAYER_Y, WIDTH, BOX_HEIGHT, SPACE);
        drawSprite(CANNON, 0, playerX, PLAYER_Y, playerX, PLAYER_Y, PLAYER_WIDTH, BOX_HEIGHT, PLAYER_COLOR);
        shownPlayerX = playerX;
    }

    private static void playerDeath(int[] bombs) {
        for (int i = 0; i < BOMBS; i++) {
            eraseBomb(bombs, i);
        }
        for (int flash = 0; flash < 10; flash++) {
            drawSprite(WRECK, flash & 1, playerX, PLAYER_Y, playerX, PLAYER_Y, PLAYER_WIDTH, BOX_HEIGHT,
                    PLAYER_COLOR);
            Delay.millis(100);
        }
        TftTouchShield.fillRect(playerX, PLAYER_Y, PLAYER_WIDTH, BOX_HEIGHT, SPACE);
        Delay.millis(400);
    }

    // ---- Formation ----

    /**
     * Moves the next living invader to the formation's current offset. Returns true when the
     * invaders have landed.
     */
    private static boolean stepFormation(byte[] living, int[] posX, int[] posY, byte[] shields) {
        int index = nextLiving(living, cursor);
        if (index < 0) {
            // A full sweep is done: choose the formation's next move.
            advanceFormation(living);
            cursor = 0;
            index = nextLiving(living, 0);
            if (index < 0) {
                return false;
            }
        }
        int i = orderToIndex(index);
        cursor = index + 1;
        int x = gridX + (i % COLUMNS) * SPACING_X;
        int y = gridY + (i / COLUMNS) * SPACING_Y;
        clearShields(shields, x, y, BOX_WIDTH, BOX_HEIGHT);
        drawSprite(kindOf(i), frame, x, y, posX[i], posY[i], BOX_WIDTH, BOX_HEIGHT, colorOf(i));
        posX[i] = x;
        posY[i] = y;
        return y + BOX_HEIGHT >= PLAYER_Y;
    }

    /** The first living invader at or after {@code order}, in bottom-row-first order; -1 if none. */
    private static int nextLiving(byte[] living, int order) {
        for (int k = order; k < INVADERS; k++) {
            if (living[orderToIndex(k)] != 0) {
                return k;
            }
        }
        return -1;
    }

    private static int orderToIndex(int order) {
        return (ROWS - 1 - order / COLUMNS) * COLUMNS + order % COLUMNS;
    }

    private static void advanceFormation(byte[] living) {
        int minColumn = COLUMNS;
        int maxColumn = -1;
        for (int i = 0; i < INVADERS; i++) {
            if (living[i] != 0) {
                minColumn = Math.min(minColumn, i % COLUMNS);
                maxColumn = Math.max(maxColumn, i % COLUMNS);
            }
        }
        frame = frame ^ 1;
        int left = gridX + minColumn * SPACING_X;
        int right = gridX + maxColumn * SPACING_X + BOX_WIDTH;
        if ((direction > 0 && right + stepPixels > WIDTH - 2) || (direction < 0 && left - stepPixels < 2)) {
            gridY = gridY + DROP;
            direction = -direction;
        } else {
            gridX = gridX + direction * stepPixels;
        }
    }

    // ---- Player and shots ----

    private static void movePlayer() {
        if (!TftTouchShield.readTouch() || TftTouchShield.touchY() < HEADER) {
            return;
        }
        int target = Math.max(0, Math.min(TftTouchShield.touchX() - PLAYER_WIDTH / 2, WIDTH - PLAYER_WIDTH));
        playerX = playerX + Math.max(-PLAYER_SPEED, Math.min(target - playerX, PLAYER_SPEED));
        if (playerX != shownPlayerX) {
            drawSprite(CANNON, 0, playerX, PLAYER_Y, shownPlayerX, PLAYER_Y, PLAYER_WIDTH, BOX_HEIGHT,
                    PLAYER_COLOR);
            shownPlayerX = playerX;
        }
        if (!bulletActive) {
            bulletActive = true;
            bulletX = playerX + PLAYER_WIDTH / 2 - 1;
            bulletY = PLAYER_Y - BULLET_HEIGHT;
            shotsFired = shotsFired + 1;
            TftTouchShield.fillRect(bulletX, bulletY, 2, BULLET_HEIGHT, TftTouchShield.WHITE);
        }
    }

    private static void moveBullet(byte[] living, int[] posX, int[] posY, byte[] shields) {
        if (!bulletActive) {
            return;
        }
        TftTouchShield.fillRect(bulletX, bulletY, 2, BULLET_HEIGHT, SPACE);
        // Small steps, so the bullet cannot skip over a shield block.
        for (int moved = 0; moved < BULLET_SPEED; moved = moved + BULLET_SUBSTEP) {
            bulletY = bulletY - BULLET_SUBSTEP;
            if (bulletY < UFO_Y) {
                bulletActive = false;
                return;
            }
            if (hitShield(shields, bulletX, bulletY) || hitShield(shields, bulletX + 1, bulletY)) {
                bulletActive = false;
                return;
            }
            if (hitUfo()) {
                bulletActive = false;
                return;
            }
            int invader = invaderAt(living, posX, posY, bulletX, bulletY);
            if (invader < 0) {
                invader = invaderAt(living, posX, posY, bulletX + 1, bulletY);
            }
            if (invader >= 0) {
                bulletActive = false;
                killInvader(living, posX, posY, invader);
                return;
            }
        }
        TftTouchShield.fillRect(bulletX, bulletY, 2, BULLET_HEIGHT, TftTouchShield.WHITE);
    }

    private static int invaderAt(byte[] living, int[] posX, int[] posY, int x, int y) {
        for (int i = 0; i < INVADERS; i++) {
            if (living[i] == 0) {
                continue;
            }
            int kind = kindOf(i);
            int left = posX[i] + artOffset(kind);
            int right = left + 2 * spriteWidth(kind);
            if (x >= left && x < right && y >= posY[i] && y < posY[i] + BOX_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    private static void killInvader(byte[] living, int[] posX, int[] posY, int i) {
        living[i] = 0;
        alive = alive - 1;
        int kind = kindOf(i);
        if (kind == SQUID) {
            score = score + 30;
        } else if (kind == CRAB) {
            score = score + 20;
        } else {
            score = score + 10;
        }
        if (burstTimer > 0) {
            eraseBurst();
        }
        burstX = posX[i];
        burstY = posY[i];
        burstTimer = 12;
        drawSprite(BURST, 0, burstX, burstY, burstX, burstY, BOX_WIDTH, BOX_HEIGHT, TftTouchShield.WHITE);
        drawHeader();
    }

    private static void eraseBurst() {
        TftTouchShield.fillRect(burstX, burstY, BOX_WIDTH, BOX_HEIGHT, SPACE);
        burstTimer = 0;
    }

    // ---- Bombs ----

    private static void dropBombs(byte[] living, int[] posX, int[] posY, int[] bombs) {
        bombCountdown = bombCountdown - 1;
        if (bombCountdown > 0) {
            return;
        }
        bombCountdown = Math.max(12, Random.nextInt(25, 70) - wave * 4);
        // Every other bomb comes from the column above the cannon, the rest from a random column.
        int column = Random.nextInt(COLUMNS);
        if (Random.nextInt(2) == 0) {
            column = Math.max(0, Math.min((playerX + PLAYER_WIDTH / 2 - gridX) / SPACING_X, COLUMNS - 1));
        }
        int shooter = -1;
        for (int row = ROWS - 1; row >= 0 && shooter < 0; row--) {
            if (living[row * COLUMNS + column] != 0) {
                shooter = row * COLUMNS + column;
            }
        }
        if (shooter < 0) {
            return;
        }
        for (int slot = 0; slot < BOMBS; slot++) {
            int base = slot * 3;
            if (bombs[base] == 0) {
                bombs[base] = 1;
                bombs[base + 1] = posX[shooter] + BOX_WIDTH / 2 - 1;
                bombs[base + 2] = posY[shooter] + BOX_HEIGHT;
                return;
            }
        }
    }

    /** Moves the bombs; returns true when one hits the cannon. */
    private static boolean moveBombs(int[] bombs, byte[] shields) {
        int speed = Math.min(2 + wave / 2, 4);
        for (int slot = 0; slot < BOMBS; slot++) {
            int base = slot * 3;
            if (bombs[base] == 0) {
                continue;
            }
            int x = bombs[base + 1];
            int y = bombs[base + 2];
            TftTouchShield.fillRect(x, y, 3, BOMB_HEIGHT, SPACE);
            for (int s = 0; s < speed; s++) {
                y = y + 1;
                int tip = y + BOMB_HEIGHT - 1;
                if (hitShield(shields, x + 1, tip)) {
                    deactivateBomb(bombs, slot);
                    break;
                }
                if (tip >= PLAYER_Y + 4 && tip < PLAYER_Y + BOX_HEIGHT && x + 2 >= playerX
                        && x < playerX + PLAYER_WIDTH) {
                    deactivateBomb(bombs, slot);
                    return true;
                }
                if (tip >= GROUND_Y - 1) {
                    deactivateBomb(bombs, slot);
                    break;
                }
            }
            if (bombs[base] == 0) {
                continue;
            }
            bombs[base + 2] = y;
            drawBomb(x, y);
        }
        return false;
    }

    private static void deactivateBomb(int[] bombs, int slot) {
        bombs[slot * 3] = 0;
    }

    private static void eraseBomb(int[] bombs, int slot) {
        int base = slot * 3;
        if (bombs[base] != 0) {
            TftTouchShield.fillRect(bombs[base + 1], bombs[base + 2], 3, BOMB_HEIGHT, SPACE);
            deactivateBomb(bombs, slot);
        }
    }

    /** A zig-zag bomb whose kink moves with its height, so it seems to spin as it falls. */
    private static void drawBomb(int x, int y) {
        int phase = (y / 3) & 1;
        for (int row = 0; row < BOMB_HEIGHT; row++) {
            int column = ((row / 2 + phase) & 1) * 2;
            TftTouchShield.fillRect(x + column, y + row, 1, 1, TftTouchShield.WHITE);
            TftTouchShield.fillRect(x + 1, y + row, 1, 1, TftTouchShield.WHITE);
        }
    }

    // ---- Shields ----

    /** Damages the shield block at pixel ({@code x}, {@code y}), if any; true when there was one. */
    private static boolean hitShield(byte[] shields, int x, int y) {
        int block = shieldBlockAt(x, y);
        if (block < 0 || shields[block] == 0) {
            return false;
        }
        int value = shields[block] - 1;
        shields[block] = (byte) value;
        drawBlock(block / SHIELD_BLOCKS, block % SHIELD_BLOCKS, value);
        return true;
    }

    /** The index into the shield array at pixel ({@code x}, {@code y}), or -1. */
    private static int shieldBlockAt(int x, int y) {
        if (y < SHIELD_Y || y >= SHIELD_Y + SHIELD_ROWS * BLOCK) {
            return -1;
        }
        for (int shield = 0; shield < SHIELDS; shield++) {
            int left = shieldX(shield);
            if (x >= left && x < left + SHIELD_COLUMNS * BLOCK) {
                int column = (x - left) / BLOCK;
                int row = (y - SHIELD_Y) / BLOCK;
                return shield * SHIELD_BLOCKS + row * SHIELD_COLUMNS + column;
            }
        }
        return -1;
    }

    /** Invaders plough through the shields: removes every block under the given rectangle. */
    private static void clearShields(byte[] shields, int x, int y, int w, int h) {
        if (y + h <= SHIELD_Y || y >= SHIELD_Y + SHIELD_ROWS * BLOCK) {
            return;
        }
        for (int shield = 0; shield < SHIELDS; shield++) {
            for (int block = 0; block < SHIELD_BLOCKS; block++) {
                int index = shield * SHIELD_BLOCKS + block;
                if (shields[index] == 0) {
                    continue;
                }
                int bx = shieldX(shield) + (block % SHIELD_COLUMNS) * BLOCK;
                int by = SHIELD_Y + (block / SHIELD_COLUMNS) * BLOCK;
                if (bx + BLOCK > x && bx < x + w && by + BLOCK > y && by < y + h) {
                    shields[index] = 0;
                    drawBlock(shield, block, 0);
                }
            }
        }
    }

    private static int shieldX(int shield) {
        return 18 + shield * 60;
    }

    private static void drawBlock(int shield, int block, int value) {
        int x = shieldX(shield) + (block % SHIELD_COLUMNS) * BLOCK;
        int y = SHIELD_Y + (block / SHIELD_COLUMNS) * BLOCK;
        if (value == 2) {
            TftTouchShield.fillRect(x, y, BLOCK, BLOCK, SHIELD_COLOR);
            return;
        }
        TftTouchShield.fillRect(x, y, BLOCK, BLOCK, SPACE);
        if (value == 1) {
            TftTouchShield.drawPixel(x, y, SHIELD_COLOR);
            TftTouchShield.drawPixel(x + 2, y + 1, SHIELD_COLOR);
            TftTouchShield.drawPixel(x + 1, y + 2, SHIELD_COLOR);
        }
    }

    // ---- Mystery ship ----

    private static void moveUfo() {
        if (ufoX < 0) {
            ufoCountdown = ufoCountdown - 1;
            if (ufoCountdown <= 0 && alive >= 8) {
                ufoDirection = 1;
                ufoX = 0;
                if (Random.nextInt(2) == 0) {
                    ufoDirection = -1;
                    ufoX = WIDTH - UFO_WIDTH - 1;
                }
                ufoCountdown = Random.nextInt(900, 1500);
                drawSprite(UFO, 0, ufoX, UFO_Y, ufoX, UFO_Y, UFO_WIDTH, UFO_HEIGHT, UFO_COLOR);
            }
            return;
        }
        int x = ufoX + ufoDirection;
        if (x < 0 || x + UFO_WIDTH > WIDTH) {
            TftTouchShield.fillRect(ufoX, UFO_Y, UFO_WIDTH, UFO_HEIGHT, SPACE);
            ufoX = -1;
            return;
        }
        drawSprite(UFO, 0, x, UFO_Y, ufoX, UFO_Y, UFO_WIDTH, UFO_HEIGHT, UFO_COLOR);
        ufoX = x;
    }

    private static boolean hitUfo() {
        if (ufoX < 0 || bulletY >= UFO_Y + UFO_HEIGHT || bulletX + 2 <= ufoX || bulletX >= ufoX + UFO_WIDTH) {
            return false;
        }
        // The arcade's famous pattern: the score depends on how many shots were fired.
        int points = 50 * (1 + (shotsFired % 4));
        if (shotsFired % 15 == 0) {
            points = 300;
        }
        score = score + points;
        TftTouchShield.fillRect(ufoX, UFO_Y, UFO_WIDTH, UFO_HEIGHT, SPACE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(UFO_COLOR, SPACE);
        TftTouchShield.setCursor(ufoX + 7, UFO_Y + 4);
        TftTouchShield.print(points);
        Delay.millis(500);
        TftTouchShield.fillRect(ufoX, UFO_Y, UFO_WIDTH, UFO_HEIGHT, SPACE);
        ufoX = -1;
        drawHeader();
        return true;
    }

    // ---- Sprites ----

    private static int kindOf(int invader) {
        int row = invader / COLUMNS;
        if (row == 0) {
            return SQUID;
        }
        if (row <= 2) {
            return CRAB;
        }
        return OCTOPUS;
    }

    private static int colorOf(int invader) {
        int row = invader / COLUMNS;
        if (row == 0) {
            return 0xF81F;
        }
        if (row <= 2) {
            return 0x07FF;
        }
        return 0xFFE0;
    }

    private static int spriteWidth(int kind) {
        if (kind == SQUID) {
            return 8;
        }
        if (kind == CRAB) {
            return 11;
        }
        if (kind == UFO) {
            return 16;
        }
        if (kind == OCTOPUS) {
            return 12;
        }
        return 13;
    }

    /** Horizontal pixel offset that centers the sprite's art in its box. */
    private static int artOffset(int kind) {
        if (kind == SQUID || kind == CRAB || kind == OCTOPUS || kind == BURST) {
            return (BOX_WIDTH - 2 * spriteWidth(kind)) / 2;
        }
        return 0;
    }

    /**
     * Streams the sprite at ({@code x}, {@code y}) in a window that also covers where it was drawn
     * last ({@code oldX}, {@code oldY}), so the uncovered strip is cleared in the same pass.
     */
    private static void drawSprite(int kind, int animation, int x, int y, int oldX, int oldY, int boxWidth,
            int boxHeight, int color) {
        int left = Math.min(x, oldX);
        int top = Math.min(y, oldY);
        int w = Math.max(x, oldX) + boxWidth - left;
        int h = Math.max(y, oldY) + boxHeight - top;
        if (!TftTouchShield.beginPixels(left, top, w, h)) {
            return;
        }
        int width = spriteWidth(kind);
        int artLeft = x + artOffset(kind);
        for (int py = top; py < top + h; py++) {
            int bits = 0;
            int ly = py - y;
            if (ly >= 0 && ly < 16) {
                bits = spriteRow(kind, animation, ly / 2);
            }
            for (int px = left; px < left + w; px++) {
                int lx = px - artLeft;
                int pixel = SPACE;
                if (bits != 0 && lx >= 0 && lx < 2 * width && ((bits >> (width - 1 - lx / 2)) & 1) != 0) {
                    pixel = color;
                }
                TftTouchShield.pushPixel(pixel);
            }
        }
    }

    private static int spriteRow(int kind, int animation, int row) {
        if (kind == SQUID) {
            return squidRow(animation, row);
        }
        if (kind == CRAB) {
            return crabRow(animation, row);
        }
        if (kind == OCTOPUS) {
            return octopusRow(animation, row);
        }
        if (kind == CANNON) {
            return cannonRow(row);
        }
        if (kind == UFO) {
            return ufoRow(row);
        }
        if (kind == BURST) {
            return burstRow(row);
        }
        return wreckRow(animation, row);
    }

    private static int squidRow(int animation, int row) {
        switch (row) {
            case 0:
                return 0b00011000;
            case 1:
                return 0b00111100;
            case 2:
                return 0b01111110;
            case 3:
                return 0b11011011;
            case 4:
                return 0b11111111;
            case 5:
                return animation == 0 ? 0b00100100 : 0b01011010;
            case 6:
                return animation == 0 ? 0b01011010 : 0b10000001;
            default:
                return animation == 0 ? 0b10100101 : 0b01000010;
        }
    }

    private static int crabRow(int animation, int row) {
        switch (row) {
            case 0:
                return 0b00100000100;
            case 1:
                return animation == 0 ? 0b00010001000 : 0b10010001001;
            case 2:
                return animation == 0 ? 0b00111111100 : 0b10111111101;
            case 3:
                return animation == 0 ? 0b01101110110 : 0b11101110111;
            case 4:
                return 0b11111111111;
            case 5:
                return animation == 0 ? 0b10111111101 : 0b01111111110;
            case 6:
                return animation == 0 ? 0b10100000101 : 0b00100000100;
            default:
                return animation == 0 ? 0b00011011000 : 0b01000000010;
        }
    }

    private static int octopusRow(int animation, int row) {
        switch (row) {
            case 0:
                return 0b000011110000;
            case 1:
                return 0b011111111110;
            case 2:
                return 0b111111111111;
            case 3:
                return 0b111001100111;
            case 4:
                return 0b111111111111;
            case 5:
                return animation == 0 ? 0b000110011000 : 0b001110011100;
            case 6:
                return animation == 0 ? 0b001101101100 : 0b011001100110;
            default:
                return animation == 0 ? 0b110000000011 : 0b001100001100;
        }
    }

    private static int cannonRow(int row) {
        switch (row) {
            case 0:
                return 0b0000001000000;
            case 1:
            case 2:
                return 0b0000011100000;
            case 3:
                return 0b0111111111110;
            default:
                return 0b1111111111111;
        }
    }

    private static int ufoRow(int row) {
        switch (row) {
            case 0:
                return 0b0000011111100000;
            case 1:
                return 0b0001111111111000;
            case 2:
                return 0b0011111111111100;
            case 3:
                return 0b0110110110110110;
            case 4:
                return 0b1111111111111111;
            case 5:
                return 0b0011100110011100;
            case 6:
                return 0b0001000000001000;
            default:
                return 0;
        }
    }

    private static int burstRow(int row) {
        switch (row) {
            case 0:
                return 0b0000100010000;
            case 1:
                return 0b0100010100010;
            case 2:
                return 0b0010000000100;
            case 3:
                return 0b0001000001000;
            case 4:
                return 0b1100000000011;
            case 5:
                return 0b0001000001000;
            case 6:
                return 0b0010010010100;
            default:
                return 0b0100100010010;
        }
    }

    private static int wreckRow(int animation, int row) {
        if (animation == 0) {
            switch (row) {
                case 0:
                    return 0b0000100000000;
                case 1:
                    return 0b0000000001000;
                case 2:
                    return 0b0001011010000;
                case 3:
                    return 0b0100111100000;
                case 4:
                    return 0b0001111110100;
                case 5:
                    return 0b0011111111000;
                case 6:
                    return 0b0111111111100;
                default:
                    return 0b1111111111110;
            }
        }
        switch (row) {
            case 0:
                return 0b0010000000100;
            case 1:
                return 0b1000001000001;
            case 2:
                return 0b0010010010000;
            case 3:
                return 0b0000110001001;
            case 4:
                return 0b1001111100000;
            case 5:
                return 0b0011111111010;
            case 6:
                return 0b0111111111100;
            default:
                return 0b1111111111111;
        }
    }

    // ---- Input ----

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
    }

    // ---- Text ----

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 3);
        TftTouchShield.print(score);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(150, 7);
        TftTouchShield.print("HI-SCORE ");
        TftTouchShield.print(best);
    }

    private static void drawLives() {
        TftTouchShield.fillRect(0, GROUND_Y + 2, WIDTH, HEIGHT - GROUND_Y - 2, SPACE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(PLAYER_COLOR, SPACE);
        TftTouchShield.setCursor(4, GROUND_Y + 7);
        TftTouchShield.print("LIVES ");
        TftTouchShield.print(lives);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(186, GROUND_Y + 7);
        TftTouchShield.print("WAVE ");
        TftTouchShield.print(wave);
    }

    private static void drawTitle() {
        showCentered("SPACE", 60, 4, TftTouchShield.WHITE);
        showCentered("INVADERS", 100, 3, TftTouchShield.GREEN);
        for (int kind = SQUID; kind <= OCTOPUS; kind++) {
            int y = 150 + kind * 26;
            drawSprite(kind, 0, 60, y, 60, y, BOX_WIDTH, BOX_HEIGHT, TftTouchShield.WHITE);
            TftTouchShield.setTextSize(2);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
            TftTouchShield.setCursor(100, y);
            TftTouchShield.print("= ");
            TftTouchShield.print(30 - kind * 10);
        }
        drawSprite(UFO, 0, 56, 228, 56, 228, UFO_WIDTH, UFO_HEIGHT, UFO_COLOR);
        TftTouchShield.setCursor(100, 228);
        TftTouchShield.print("= ?");
        showCentered("Hold to move and fire", 272, 1, TftTouchShield.YELLOW);
        showCentered("Tap to start", 290, 1, TftTouchShield.YELLOW);
    }

    private static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
