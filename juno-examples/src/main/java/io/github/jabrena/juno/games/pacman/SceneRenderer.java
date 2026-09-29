package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The redraw engine: only the rectangles around moving sprites are redrawn, each streamed with
 * {@code beginPixels}/{@code pushPixel}, compositing Pac-Man and the ghosts (their pixel shapes live
 * in {@link Sprites}) over the maze pixel {@link MazePixels} computes from the tile map, so dots and
 * walls are restored exactly where a sprite has passed.
 */
final class SceneRenderer {
    static final int SPACE = TftTouchShield.BLACK;
    static final int PAC_COLOR = TftTouchShield.YELLOW;
    static final int FRIGHT_COLOR = 0x211F;
    static final int FRIGHT_FACE = 0xFDD5;
    static final int EYE_PUPIL = 0x211F;

    private SceneRenderer() {
    }

    static void drawMaze(byte[] tiles, int[] ghosts) {
        redrawArea(tiles, ghosts, -Maze.MAZE_X, 0, Maze.COLUMNS * Maze.TILE + 2 * Maze.MAZE_X, Maze.ROWS * Maze.TILE);
    }

    /** Redraws every actor that moved or changed looks; with {@code force}, all of them. */
    static void redrawActors(byte[] tiles, int[] ghosts, boolean force) {
        for (int g = 0; g < Session.GHOSTS; g++) {
            int base = g * Session.G_STRIDE;
            int x = ghosts[base + Session.G_X];
            int y = ghosts[base + Session.G_Y];
            int look = Sprites.ghostLook(ghosts, g);
            if (force || x != ghosts[base + Session.G_SHOWN_X] || y != ghosts[base + Session.G_SHOWN_Y]
                    || look != ghosts[base + Session.G_SHOWN_LOOK]) {
                redrawMoved(tiles, ghosts, ghosts[base + Session.G_SHOWN_X], ghosts[base + Session.G_SHOWN_Y], x, y);
                ghosts[base + Session.G_SHOWN_X] = x;
                ghosts[base + Session.G_SHOWN_Y] = y;
                ghosts[base + Session.G_SHOWN_LOOK] = look;
            }
        }
        int look = Sprites.pacLook();
        if (force || Session.pacX != Session.pacShownX || Session.pacY != Session.pacShownY
                || look != Session.pacShownLook) {
            redrawMoved(tiles, ghosts, Session.pacShownX, Session.pacShownY, Session.pacX, Session.pacY);
            Session.pacShownX = Session.pacX;
            Session.pacShownY = Session.pacY;
            Session.pacShownLook = look;
        }
    }

    /** Redraws the union of a sprite's old and new rectangles (just the new one when far apart). */
    private static void redrawMoved(byte[] tiles, int[] ghosts, int oldX, int oldY, int x, int y) {
        if (Math.abs(oldX - x) > 2 * Session.HALF || Math.abs(oldY - y) > 2 * Session.HALF) {
            if (oldX > -1000) {
                redrawArea(tiles, ghosts, oldX - Session.HALF, oldY - Session.HALF, 2 * Session.HALF + 1,
                        2 * Session.HALF + 1);
            }
            redrawArea(tiles, ghosts, x - Session.HALF, y - Session.HALF, 2 * Session.HALF + 1, 2 * Session.HALF + 1);
            return;
        }
        int left = Math.min(oldX, x) - Session.HALF;
        int top = Math.min(oldY, y) - Session.HALF;
        redrawArea(tiles, ghosts, left, top, Math.max(oldX, x) + Session.HALF + 1 - left,
                Math.max(oldY, y) + Session.HALF + 1 - top);
    }

    /** Streams a maze-space rectangle, clipped to the screen, compositing the actors over the maze. */
    static void redrawArea(byte[] tiles, int[] ghosts, int x, int y, int w, int h) {
        int left = Math.max(x, -Maze.MAZE_X);
        int top = Math.max(y, 0);
        int right = Math.min(x + w, Maze.COLUMNS * Maze.TILE + Maze.MAZE_X);
        int bottom = Math.min(y + h, Maze.ROWS * Maze.TILE);
        if (right <= left || bottom <= top) {
            return;
        }
        if (!TftTouchShield.beginPixels(Maze.MAZE_X + left, Maze.MAZE_Y + top, right - left, bottom - top)) {
            return;
        }
        for (int py = top; py < bottom; py++) {
            for (int px = left; px < right; px++) {
                TftTouchShield.pushPixel(pixelAt(tiles, ghosts, px, py));
            }
        }
    }

    private static int pixelAt(byte[] tiles, int[] ghosts, int px, int py) {
        if (Session.ghostsVisible) {
            for (int g = 0; g < Session.GHOSTS; g++) {
                int base = g * Session.G_STRIDE;
                int dx = px - ghosts[base + Session.G_X];
                int dy = py - ghosts[base + Session.G_Y];
                if (dx >= -Session.HALF && dx <= Session.HALF && dy >= -Session.HALF && dy <= Session.HALF) {
                    int color = Sprites.ghostPixel(ghosts, g, dx, dy);
                    if (color >= 0) {
                        return color;
                    }
                }
            }
        }
        if (Session.pacVisible) {
            int dx = px - Session.pacX;
            int dy = py - Session.pacY;
            if (dx >= -Session.HALF && dx <= Session.HALF && dy >= -Session.HALF && dy <= Session.HALF
                    && Sprites.pacPixel(dx, dy)) {
                return PAC_COLOR;
            }
        }
        return MazePixels.backgroundAt(tiles, px, py);
    }
}
