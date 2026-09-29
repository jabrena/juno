package io.github.jabrena.juno.games.pacman;

/**
 * The arcade's 28x31-tile maze: its layout and tile lookups. The pixel-level wall outline, door and
 * dot rendering that {@link SceneRenderer} composites Pac-Man and the ghosts over lives in
 * {@link MazePixels}.
 *
 * <p>{@link #mazeRow} is split top/bottom to keep each half's cyclomatic complexity in check; the
 * bottom half shares several rows with the top verbatim, so it delegates back to {@link #mazeRowTop}
 * for those instead of repeating the literal.
 */
final class Maze {
    static final int COLUMNS = 28;
    static final int ROWS = 31;
    static final int TILES = COLUMNS * ROWS;
    static final int TILE = 8;
    static final int MAZE_X = 8;
    static final int MAZE_Y = 32;
    static final int TUNNEL_ROW = 14;
    static final int TOTAL_DOTS = 244;

    static final int EMPTY = 0;
    static final int WALL = 1;
    static final int DOT = 2;
    static final int ENERGIZER = 3;
    static final int DOOR = 4;

    private Maze() {
    }

    static void loadMaze(byte[] tiles) {
        for (int row = 0; row < ROWS; row++) {
            String line = mazeRow(row);
            for (int column = 0; column < COLUMNS; column++) {
                char c = line.charAt(column);
                int tile = EMPTY;
                if (c == '#') {
                    tile = WALL;
                } else if (c == '.') {
                    tile = DOT;
                } else if (c == 'o') {
                    tile = ENERGIZER;
                } else if (c == '-') {
                    tile = DOOR;
                }
                tiles[row * COLUMNS + column] = (byte) tile;
            }
        }
    }

    private static String mazeRow(int row) {
        return row < 15 ? mazeRowTop(row) : mazeRowBottom(row);
    }

    private static String mazeRowTop(int row) {
        switch (row) {
            case 0:
                return "############################";
            case 1:
                return "#............##............#";
            case 2:
            case 4:
                return "#.####.#####.##.#####.####.#";
            case 3:
                return "#o####.#####.##.#####.####o#";
            case 5:
                return "#..........................#";
            case 6:
            case 7:
                return "#.####.##.########.##.####.#";
            case 8:
                return "#......##....##....##......#";
            case 9:
                return "######.##### ## #####.######";
            case 10:
                return "     #.##### ## #####.#     ";
            case 11:
                return "     #.##          ##.#     ";
            case 12:
                return "     #.## ###--### ##.#     ";
            case 13:
                return "######.## #      # ##.######";
            default:
                // Only row 14 (the tunnel row) reaches here; mazeRow() routes every other row above
                // 15 to one of the explicit cases.
                return "      .   #      #   .      ";
        }
    }

    private static String mazeRowBottom(int row) {
        switch (row) {
            case 15:
                return mazeRowTop(13);
            case 16:
            case 18:
                return "     #.## ######## ##.#     ";
            case 17:
                return mazeRowTop(11);
            case 19:
                return "######.## ######## ##.######";
            case 20:
                return mazeRowTop(1);
            case 21:
            case 22:
                return mazeRowTop(2);
            case 23:
                return "#o..##.......  .......##..o#";
            case 24:
            case 25:
                return "###.##.##.########.##.##.###";
            case 26:
                return mazeRowTop(8);
            case 29:
                return mazeRowTop(5);
            case 30:
                return mazeRowTop(0);
            default:
                return "#.##########.##.##########.#";
        }
    }

    /** The tile at a column and row; past the side edges only the tunnel row is open. */
    static int tileAt(byte[] tiles, int column, int row) {
        if (row < 0 || row >= ROWS) {
            return WALL;
        }
        if (column < 0 || column >= COLUMNS) {
            return row == TUNNEL_ROW ? EMPTY : WALL;
        }
        return tiles[row * COLUMNS + column];
    }

    static boolean isWall(byte[] tiles, int column, int row) {
        return tileAt(tiles, column, row) == WALL;
    }
}
