package io.github.jabrena.juno.games.pacman;

/**
 * The maze's own pixel colors: walls are outlines traced at run time from each tile's neighbours
 * (a wall tile minus a 3-pixel margin along every side that faces an open tile), plus the door and
 * the dots. Split out of {@link Maze} to keep that class's own cyclomatic complexity in check.
 */
final class MazePixels {
    static final int WALL_COLOR = 0x211F;
    static final int DOT_COLOR = 0xFDD5;
    static final int DOOR_COLOR = 0xFDDF;

    static int wallColor = WALL_COLOR;

    private MazePixels() {
    }

    /**
     * Whether maze pixel ({@code px}, {@code py}) lies inside the shrunken wall shape: a wall tile
     * minus a 3-pixel margin along each side (and corner) that faces an open tile.
     */
    private static boolean insideWall(byte[] tiles, int px, int py) {
        int column = Math.floorDiv(px, Maze.TILE);
        int row = Math.floorDiv(py, Maze.TILE);
        if (!Maze.isWall(tiles, column, row)) {
            return false;
        }
        int lx = px - column * Maze.TILE;
        int ly = py - row * Maze.TILE;
        boolean near = lx < 3;
        boolean far = lx > 4;
        boolean top = ly < 3;
        boolean bottom = ly > 4;
        if ((top && !Maze.isWall(tiles, column, row - 1)) || (bottom && !Maze.isWall(tiles, column, row + 1))
                || (near && !Maze.isWall(tiles, column - 1, row)) || (far && !Maze.isWall(tiles, column + 1, row))) {
            return false;
        }
        if ((top && near && !Maze.isWall(tiles, column - 1, row - 1))
                || (top && far && !Maze.isWall(tiles, column + 1, row - 1))
                || (bottom && near && !Maze.isWall(tiles, column - 1, row + 1))
                || (bottom && far && !Maze.isWall(tiles, column + 1, row + 1))) {
            return false;
        }
        return true;
    }

    /** The maze's own color at a pixel: wall outline, door, dot, energizer or black. */
    static int backgroundAt(byte[] tiles, int px, int py) {
        int column = Math.floorDiv(px, Maze.TILE);
        int row = Math.floorDiv(py, Maze.TILE);
        int tile = Maze.tileAt(tiles, column, row);
        if (tile == Maze.WALL) {
            return wallOutlineAt(tiles, px, py);
        }
        if (tile == Maze.DOOR) {
            return doorAt(py, row);
        }
        if (tile == Maze.DOT) {
            return dotAt(px, py, column, row);
        }
        if (tile == Maze.ENERGIZER) {
            return energizerAt(px, py, column, row);
        }
        return SceneRenderer.SPACE;
    }

    private static int wallOutlineAt(byte[] tiles, int px, int py) {
        if (insideWall(tiles, px, py) && (!insideWall(tiles, px - 1, py) || !insideWall(tiles, px + 1, py)
                || !insideWall(tiles, px, py - 1) || !insideWall(tiles, px, py + 1))) {
            return wallColor;
        }
        return SceneRenderer.SPACE;
    }

    private static int doorAt(int py, int row) {
        int ly = py - row * Maze.TILE;
        return ly == 3 || ly == 4 ? DOOR_COLOR : SceneRenderer.SPACE;
    }

    private static int dotAt(int px, int py, int column, int row) {
        int lx = px - column * Maze.TILE;
        int ly = py - row * Maze.TILE;
        return (lx == 3 || lx == 4) && (ly == 3 || ly == 4) ? DOT_COLOR : SceneRenderer.SPACE;
    }

    private static int energizerAt(int px, int py, int column, int row) {
        int lx = px - column * Maze.TILE;
        int ly = py - row * Maze.TILE;
        int ex = 2 * lx - 7;
        int ey = 2 * ly - 7;
        return ex * ex + ey * ey <= 50 ? DOT_COLOR : SceneRenderer.SPACE;
    }
}
