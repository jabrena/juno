import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.LedMatrix;
import io.github.jabrena.juno.api.LedMatrixShapes;

/**
 * The classic bouncing-ball animation on the UNO R4 WiFi's 12x8 LED matrix: a small circle, drawn
 * with {@code LedMatrixShapes.fillCircle} at radius 1, moves diagonally one pixel per tick and
 * reverses direction whenever it reaches an edge of the matrix.
 *
 * <p>The starting position and the starting diagonal (one of the four 45-degree "angles" reachable
 * with unit-speed integer motion) are randomized each run: {@code Clock.micros()} at boot seeds a
 * linear congruential generator (the same one {@code LedMatrixSnake} uses for food placement),
 * since Juno v0.1 has no {@code java.util.Random}.
 */
public final class LedMatrixBouncingBall {
    private static final int WIDTH = 12;
    private static final int HEIGHT = 8;
    private static final int RADIUS = 1;
    private static final int MIN_X = RADIUS;
    private static final int MAX_X = WIDTH - 1 - RADIUS;
    private static final int MIN_Y = RADIUS;
    private static final int MAX_Y = HEIGHT - 1 - RADIUS;

    public static void main(String[] args) {
        LedMatrix.begin();

        int rngState = Clock.micros();
        rngState = rngState * 1103515245 + 12345;
        int ballX = MIN_X + rawMod(rngState, MAX_X - MIN_X + 1);
        rngState = rngState * 1103515245 + 12345;
        int ballY = MIN_Y + rawMod(rngState, MAX_Y - MIN_Y + 1);
        rngState = rngState * 1103515245 + 12345;
        int velocityX = 1;
        if (rawMod(rngState, 2) == 0) {
            velocityX = -1;
        }
        rngState = rngState * 1103515245 + 12345;
        int velocityY = 1;
        if (rawMod(rngState, 2) == 0) {
            velocityY = -1;
        }

        while (true) {
            int word0 = LedMatrixShapes.fillCircle(0, 0, ballX, ballY, RADIUS);
            int word1 = LedMatrixShapes.fillCircle(0, 1, ballX, ballY, RADIUS);
            int word2 = LedMatrixShapes.fillCircle(0, 2, ballX, ballY, RADIUS);
            LedMatrix.loadFrame(word0, word1, word2);
            Delay.millis(120);
            LedMatrix.clear();

            ballX = ballX + velocityX;
            ballY = ballY + velocityY;
            if (ballX <= MIN_X || ballX >= MAX_X) {
                velocityX = -velocityX;
                ballX = ballX + velocityX;
            }
            if (ballY <= MIN_Y || ballY >= MAX_Y) {
                velocityY = -velocityY;
                ballY = ballY + velocityY;
            }
        }
    }

    private static int rawMod(int value, int modulus) {
        int remainder = value % modulus;
        if (remainder < 0) {
            remainder = remainder + modulus;
        }
        return remainder;
    }
}
