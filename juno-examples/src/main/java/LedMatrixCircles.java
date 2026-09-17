import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.LedMatrix;
import io.github.jabrena.juno.api.LedMatrixShapes;

/**
 * Cycles a filled circle and an outlined circle, centered on the UNO R4 WiFi's 12x8 LED matrix,
 * using {@code LedMatrixShapes}.
 */
public final class LedMatrixCircles {
    private static final int CENTER_X = 5;
    private static final int CENTER_Y = 3;
    private static final int RADIUS = 3;

    public static void main(String[] args) {
        LedMatrix.begin();

        while (true) {
            int word0 = LedMatrixShapes.fillCircle(0, 0, CENTER_X, CENTER_Y, RADIUS);
            int word1 = LedMatrixShapes.fillCircle(0, 1, CENTER_X, CENTER_Y, RADIUS);
            int word2 = LedMatrixShapes.fillCircle(0, 2, CENTER_X, CENTER_Y, RADIUS);
            LedMatrix.loadFrame(word0, word1, word2);
            Delay.millis(800);
            LedMatrix.clear();

            word0 = LedMatrixShapes.drawCircle(0, 0, CENTER_X, CENTER_Y, RADIUS);
            word1 = LedMatrixShapes.drawCircle(0, 1, CENTER_X, CENTER_Y, RADIUS);
            word2 = LedMatrixShapes.drawCircle(0, 2, CENTER_X, CENTER_Y, RADIUS);
            LedMatrix.loadFrame(word0, word1, word2);
            Delay.millis(800);
            LedMatrix.clear();
        }
    }
}
