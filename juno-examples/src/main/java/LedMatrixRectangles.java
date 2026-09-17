import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.LedMatrix;
import io.github.jabrena.juno.api.LedMatrixShapes;

/**
 * Cycles a filled square, an outlined square, a filled rectangle, and an outlined rectangle on the
 * UNO R4 WiFi's 12x8 LED matrix using {@code LedMatrixShapes}.
 */
public final class LedMatrixRectangles {
    public static void main(String[] args) {
        LedMatrix.begin();

        while (true) {
            int word0 = LedMatrixShapes.fillRect(0, 0, 3, 1, 6, 6);
            int word1 = LedMatrixShapes.fillRect(0, 1, 3, 1, 6, 6);
            int word2 = LedMatrixShapes.fillRect(0, 2, 3, 1, 6, 6);
            LedMatrix.loadFrame(word0, word1, word2);
            Delay.millis(800);
            LedMatrix.clear();

            word0 = LedMatrixShapes.drawRect(0, 0, 3, 1, 6, 6);
            word1 = LedMatrixShapes.drawRect(0, 1, 3, 1, 6, 6);
            word2 = LedMatrixShapes.drawRect(0, 2, 3, 1, 6, 6);
            LedMatrix.loadFrame(word0, word1, word2);
            Delay.millis(800);
            LedMatrix.clear();

            word0 = LedMatrixShapes.fillRect(0, 0, 1, 2, 10, 4);
            word1 = LedMatrixShapes.fillRect(0, 1, 1, 2, 10, 4);
            word2 = LedMatrixShapes.fillRect(0, 2, 1, 2, 10, 4);
            LedMatrix.loadFrame(word0, word1, word2);
            Delay.millis(800);
            LedMatrix.clear();

            word0 = LedMatrixShapes.drawRect(0, 0, 1, 2, 10, 4);
            word1 = LedMatrixShapes.drawRect(0, 1, 1, 2, 10, 4);
            word2 = LedMatrixShapes.drawRect(0, 2, 1, 2, 10, 4);
            LedMatrix.loadFrame(word0, word1, word2);
            Delay.millis(800);
            LedMatrix.clear();
        }
    }
}
