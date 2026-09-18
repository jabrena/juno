import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedMatrix;
import io.github.jabrena.juno.api.led.LedMatrixShapes;
import io.github.jabrena.juno.api.led.LedMatrixTransform;

/**
 * Draws a triangle on the UNO R4 WiFi's 12x8 LED matrix and steps it through the four 90-degree
 * orientations using {@code LedMatrixTransform}'s integer rotation matrix, cycling forever.
 */
public final class LedMatrixSpinningTriangle {
    private static final int PIVOT_X = 5;
    private static final int PIVOT_Y = 3;
    private static final int APEX_X = 5;
    private static final int APEX_Y = 0;
    private static final int BASE_LEFT_X = 2;
    private static final int BASE_LEFT_Y = 6;
    private static final int BASE_RIGHT_X = 8;
    private static final int BASE_RIGHT_Y = 6;

    public static void main(String[] args) {
        LedMatrix.begin();

        while (true) {
            int quarterTurns = 0;
            while (quarterTurns < 4) {
                int x1 = LedMatrixTransform.rotateX(APEX_X, APEX_Y, PIVOT_X, PIVOT_Y, quarterTurns);
                int y1 = LedMatrixTransform.rotateY(APEX_X, APEX_Y, PIVOT_X, PIVOT_Y, quarterTurns);
                int x2 = LedMatrixTransform.rotateX(BASE_LEFT_X, BASE_LEFT_Y, PIVOT_X, PIVOT_Y, quarterTurns);
                int y2 = LedMatrixTransform.rotateY(BASE_LEFT_X, BASE_LEFT_Y, PIVOT_X, PIVOT_Y, quarterTurns);
                int x3 = LedMatrixTransform.rotateX(BASE_RIGHT_X, BASE_RIGHT_Y, PIVOT_X, PIVOT_Y, quarterTurns);
                int y3 = LedMatrixTransform.rotateY(BASE_RIGHT_X, BASE_RIGHT_Y, PIVOT_X, PIVOT_Y, quarterTurns);

                int word0 = LedMatrixShapes.drawTriangle(0, 0, x1, y1, x2, y2, x3, y3);
                int word1 = LedMatrixShapes.drawTriangle(0, 1, x1, y1, x2, y2, x3, y3);
                int word2 = LedMatrixShapes.drawTriangle(0, 2, x1, y1, x2, y2, x3, y3);

                LedMatrix.loadFrame(word0, word1, word2);
                Delay.millis(600);
                LedMatrix.clear();
                quarterTurns = quarterTurns + 1;
            }
        }
    }
}
