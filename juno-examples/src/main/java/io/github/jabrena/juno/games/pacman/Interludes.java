package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The animated title screen and the non-gameplay cards: ready, death, level-clear and game over. */
final class Interludes {
    private static final int TITLE_X = 20;
    private static final int TITLE_Y = 92;
    private static final int TITLE_WIDTH = 200;
    private static final int TITLE_HEIGHT = 50;
    private static final int TITLE_MILLIS = 110;
    private static final int SHADOW_COLOR = 0x18E3;

    private Interludes() {
    }

    private static final String[] GHOST_NAMES = {"-BLINKY", "-PINKY", "-INKY", "-CLYDE"};
    private static final String[] GHOST_NICKNAMES = {"\"SHADOW\"", "\"SPEEDY\"", "\"BASHFUL\"", "\"POKEY\""};
    private static final int CAST_ROW_HEIGHT = 26;
    private static final int CAST_TOP = 76;
    private static final int CAST_ICON_X = 26;
    private static final int CAST_TEXT_X = 46;
    private static final int CHASE_ROW_Y = 110;

    /**
     * A self-contained cover, after the arcade's own attract sequence: the title zooms in over a
     * soft drop shadow, the cast is introduced one ghost at a time the way the arcade's
     * "CHARACTER / NICKNAME" screen does, then two of them chase Pac-Man across the screen, an
     * energizer at the wall turns the tables, and he chases them back.
     */
    static void cover() {
        TftTouchShield.fillScreen(SceneRenderer.SPACE);
        zoomTitle();
        characterIntro();
        chaseDemo();
        Hud.showCentered("PAC-MAN", 70, 2, SceneRenderer.PAC_COLOR);
        Sprites.drawPacIcon(Session.WIDTH / 2, CHASE_ROW_Y, 10, true, false);
        Hud.showCentered("TAP TO CHOOSE A PILOT", 250, 1, TftTouchShield.YELLOW);
        Hud.showCentered("Hold beside Pac-Man to steer", 264, 1, TftTouchShield.WHITE);
    }

    /** The arcade's own cast list, revealed one ghost at a time in its own color. */
    private static void characterIntro() {
        TftTouchShield.fillScreen(SceneRenderer.SPACE);
        Hud.showCentered("CHARACTER / NICKNAME", 46, 1, TftTouchShield.WHITE);
        Delay.millis(300);
        for (int g = 0; g < Session.GHOSTS; g++) {
            int y = CAST_TOP + g * CAST_ROW_HEIGHT;
            int color = Sprites.ghostColor(g);
            Sprites.drawGhostIcon(CAST_ICON_X, y + 3, 8, color);
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(color, SceneRenderer.SPACE);
            TftTouchShield.setCursor(CAST_TEXT_X, y);
            TftTouchShield.print(GHOST_NAMES[g]);
            TftTouchShield.print("   ");
            TftTouchShield.print(GHOST_NICKNAMES[g]);
            Delay.millis(650);
        }
        Delay.millis(900);
        TftTouchShield.fillScreen(SceneRenderer.SPACE);
    }

    /** Ghosts chase Pac-Man right, an energizer at the wall turns them blue, then he chases them back. */
    private static void chaseDemo() {
        int y = CHASE_ROW_Y;
        int radius = 9;
        int left = 20;
        int right = 206;
        int steps = 14;
        int gap = 22;

        for (int step = 0; step <= steps; step++) {
            int x = left + (right - left) * step / steps;
            drawChaseFrame(x, y, radius, gap, step, false, true);
            Delay.millis(55);
        }
        energizerFlash(right, y);
        for (int step = 0; step <= steps; step++) {
            int x = right - (right - left) * step / steps;
            drawChaseFrame(x, y, radius, gap, step, true, false);
            Delay.millis(55);
        }
        TftTouchShield.fillRect(0, y - radius - 2, Session.WIDTH, 2 * radius + 4, SceneRenderer.SPACE);
    }

    /** The energizer Pac-Man just reached, pulsing before it turns the chasers blue. */
    private static void energizerFlash(int x, int y) {
        for (int pulse = 0; pulse < 3; pulse++) {
            TftTouchShield.fillCircle(x, y, 4, MazePixels.DOT_COLOR);
            Delay.millis(80);
            TftTouchShield.fillCircle(x, y, 4, SceneRenderer.SPACE);
            Delay.millis(80);
        }
    }

    /** One frame: Pac-Man at {@code x}, two ghosts trailing him by one and two gaps. */
    private static void drawChaseFrame(int x, int y, int radius, int gap, int step, boolean frightened,
            boolean facingRight) {
        TftTouchShield.fillRect(0, y - radius - 2, Session.WIDTH, 2 * radius + 4, SceneRenderer.SPACE);
        int firstGhost = frightened ? SceneRenderer.FRIGHT_COLOR : 0xF800;
        int secondGhost = frightened ? SceneRenderer.FRIGHT_COLOR : 0xFDDF;
        Sprites.drawGhostIcon(x - gap, y, radius - 1, firstGhost);
        Sprites.drawGhostIcon(x - 2 * gap, y, radius - 1, secondGhost);
        Sprites.drawPacIcon(x, y, radius, (step & 1) == 0, !facingRight);
    }

    /** The logo grows out of the chase, flashes white, then settles over a soft drop shadow. */
    private static void zoomTitle() {
        titleFrame(1, 132, TftTouchShield.CYAN);
        titleFrame(2, 120, TftTouchShield.RED);
        titleFrame(2, 118, TftTouchShield.WHITE);
        TftTouchShield.fillRect(TITLE_X, TITLE_Y, TITLE_WIDTH, TITLE_HEIGHT, SceneRenderer.SPACE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(SHADOW_COLOR, SceneRenderer.SPACE);
        TftTouchShield.setCursor(titleX("PAC-MAN", 2) + 2, 120);
        TftTouchShield.print("PAC-MAN");
        Hud.showCentered("PAC-MAN", 118, 2, SceneRenderer.PAC_COLOR);
        Delay.millis(TITLE_MILLIS);
        // Let the settled title breathe before the cast list replaces it.
        Delay.millis(500);
    }

    private static void titleFrame(int size, int y, int color) {
        TftTouchShield.fillRect(TITLE_X, TITLE_Y, TITLE_WIDTH, TITLE_HEIGHT, SceneRenderer.SPACE);
        Hud.showCentered("PAC-MAN", y, size, color);
        Delay.millis(TITLE_MILLIS);
    }

    private static int titleX(String text, int size) {
        return (Session.WIDTH - text.length() * 6 * size) / 2;
    }

    static void readyCard() {
        Hud.showCentered("READY!", 17 * Maze.TILE + Maze.MAZE_Y, 1, SceneRenderer.PAC_COLOR);
        Delay.millis(1800);
    }

    static void dieAnimation(byte[] tiles, int[] ghosts) {
        Delay.millis(800);
        Session.ghostsVisible = false;
        Session.pacDir = Session.UP;
        SceneRenderer.redrawActors(tiles, ghosts, true);
        for (int step = 1; step <= 12; step++) {
            Session.pacDeath = step;
            SceneRenderer.redrawActors(tiles, ghosts, true);
            Delay.millis(90);
        }
        Session.pacVisible = false;
        SceneRenderer.redrawActors(tiles, ghosts, true);
        Delay.millis(600);
    }

    static void flashMaze(byte[] tiles, int[] ghosts) {
        Session.ghostsVisible = false;
        Session.pacVisible = false;
        SceneRenderer.redrawActors(tiles, ghosts, true);
        for (int flash = 0; flash < 2; flash++) {
            MazePixels.wallColor = TftTouchShield.WHITE;
            if ((flash & 1) != 0) {
                MazePixels.wallColor = MazePixels.WALL_COLOR;
            }
            SceneRenderer.drawMaze(tiles, ghosts);
            Delay.millis(200);
        }
        MazePixels.wallColor = MazePixels.WALL_COLOR;
    }

    static void gameOver() {
        Hud.drawHeader();
        Hud.showCentered("GAME  OVER", 17 * Maze.TILE + Maze.MAZE_Y, 1, TftTouchShield.RED);
        Delay.millis(1500);
    }
}
