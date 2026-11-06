package io.github.jabrena.juno.robotics.cv.aruco;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * An ArUco fiducial marker from the {@code DICT_4X4_50} dictionary: ids 0 to 49, each a 6x6 grid of cells
 * whose outer ring is black and whose inner 4x4 cells encode the id. Marker cells are read row by row, most
 * significant bit first, with a set bit drawn white.
 *
 * <p>{@link #draw} paints one marker on a {@link TftTouchShield} with a one-cell white quiet zone around it, the
 * margin ArUco detectors need. Painting uses only {@code fillRect} calls and integer arithmetic, so it allocates
 * nothing.
 */
public final class ArucoMarker {
    /** Number of markers in the dictionary; valid ids are {@code 0} to {@code MARKERS - 1}. */
    public static final int MARKERS = 50;

    /** Cells per side of the marker, including its black border. */
    static final int CELLS = 6;

    /** Rotation-0 inner 4x4 code of each marker: the row-major bits, white = 1. */
    private static final int[] CODES = {
        0xB532, 0x0F9A, 0x332D, 0x9946, 0x549E, 0x79CD, 0x9E2E, 0xC4F2, 0xFEDA, 0xCF56,
        0xF991, 0x11A7, 0x0EB7, 0x2A0F, 0x24B1, 0x263E, 0x4665, 0x6600, 0x6C5E, 0x76AF,
        0x868B, 0xB02B, 0xCCD5, 0xDD82, 0xFE47, 0x9471, 0xACE4, 0xA554, 0x2123, 0x346F,
        0x4415, 0x57B2, 0x9ECF, 0xF0CB, 0x08AE, 0x0929, 0x1875, 0x04FF, 0x0DF6, 0x1C5A,
        0x1718, 0x2A28, 0x328C, 0x38B2, 0x24E8, 0x2EEB, 0x2D3F, 0x4B64, 0x502E, 0x5013
    };

    private ArucoMarker() {}

    /**
     * Draws marker {@code id} with its black border's top-left corner at ({@code x}, {@code y}), each cell
     * {@code cellSize} pixels square, and a white quiet zone of one cell around it. The caller places the marker;
     * the quiet zone may extend up to one cell beyond the given position.
     *
     * @throws IllegalArgumentException if {@code id} is outside {@code 0} to {@code MARKERS - 1}
     */
    public static void draw(TftTouchShield tft, int id, int x, int y, int cellSize) {
        checkId(id);
        int side = CELLS * cellSize;
        tft.fillRect(x - cellSize, y - cellSize, side + 2 * cellSize, side + 2 * cellSize, TftTouchShield.WHITE);
        tft.fillRect(x, y, side, side, TftTouchShield.BLACK);
        for (int row = 1; row < CELLS - 1; row++) {
            for (int col = 1; col < CELLS - 1; col++) {
                if (isWhite(id, row, col)) {
                    tft.fillRect(x + col * cellSize, y + row * cellSize, cellSize, cellSize, TftTouchShield.WHITE);
                }
            }
        }
    }

    /** {@code true} if the cell at {@code row}, {@code col} (0 to 5 each) of marker {@code id} is white. */
    static boolean isWhite(int id, int row, int col) {
        checkId(id);
        if (row == 0 || row == CELLS - 1 || col == 0 || col == CELLS - 1) {
            return false;
        }
        int bit = 15 - ((row - 1) * 4 + (col - 1));
        return ((CODES[id] >> bit) & 1) != 0;
    }

    private static void checkId(int id) {
        if (id < 0 || id >= MARKERS) {
            throw new IllegalArgumentException("ArUco DICT_4X4_50 id must be 0 to 49");
        }
    }
}
