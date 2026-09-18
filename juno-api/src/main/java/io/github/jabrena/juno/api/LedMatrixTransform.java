package io.github.jabrena.juno.api;

/**
 * Rotates the points of a {@link LedMatrixShapes} shape around a pivot before drawing it, using the
 * standard 2D rotation matrix {@code [[cos t, -sin t], [sin t, cos t]]}. Since Juno v0.1 has no
 * {@code float}/{@code double} for arbitrary-angle trigonometry, rotation is restricted to
 * {@code quarterTurns} steps of 90 degrees, where {@code cos}/{@code sin} are always exactly
 * {@code -1}, {@code 0}, or {@code 1} and the matrix multiplication stays pure integer math. On the
 * matrix's y-down pixel grid, increasing {@code quarterTurns} steps the shape clockwise on screen.
 */
public final class LedMatrixTransform {
    private LedMatrixTransform() {
    }

    /** The x-coordinate of (x, y) rotated quarterTurns * 90 degrees around (pivotX, pivotY). */
    public static int rotateX(int x, int y, int pivotX, int pivotY, int quarterTurns) {
        int dx = x - pivotX;
        int dy = y - pivotY;
        int turns = normalizeTurns(quarterTurns);
        if (turns == 0) {
            return pivotX + dx;
        }
        if (turns == 1) {
            return pivotX - dy;
        }
        if (turns == 2) {
            return pivotX - dx;
        }
        return pivotX + dy;
    }

    /** The y-coordinate of (x, y) rotated quarterTurns * 90 degrees around (pivotX, pivotY). */
    public static int rotateY(int x, int y, int pivotX, int pivotY, int quarterTurns) {
        int dx = x - pivotX;
        int dy = y - pivotY;
        int turns = normalizeTurns(quarterTurns);
        if (turns == 0) {
            return pivotY + dy;
        }
        if (turns == 1) {
            return pivotY + dx;
        }
        if (turns == 2) {
            return pivotY - dy;
        }
        return pivotY - dx;
    }

    private static int normalizeTurns(int quarterTurns) {
        int turns = quarterTurns % 4;
        if (turns < 0) {
            turns = turns + 4;
        }
        return turns;
    }
}
