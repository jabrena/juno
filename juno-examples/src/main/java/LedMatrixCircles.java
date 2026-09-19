import io.github.jabrena.juno.api.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.api.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * Cycles a filled circle and an outlined circle, centered on the UNO R4 WiFi's 12x8 LED matrix,
 * using {@code LedCanvas}.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class LedMatrixCircles {
    private static final int CENTER_X = 5;
    private static final int CENTER_Y = 3;
    private static final int RADIUS = 3;

    public static void main(String[] args) {
        LedMatrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];

        while (true) {
            LedCanvas.clear(frame);
            LedCanvas.fillCircle(frame, CENTER_X, CENTER_Y, RADIUS);
            LedCanvas.show(frame);
            Delay.millis(800);
            LedMatrix.clear();

            LedCanvas.clear(frame);
            LedCanvas.drawCircle(frame, CENTER_X, CENTER_Y, RADIUS);
            LedCanvas.show(frame);
            Delay.millis(800);
            LedMatrix.clear();
        }
    }
}
