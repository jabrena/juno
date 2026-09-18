import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * Cycles a filled square, an outlined square, a filled rectangle, and an outlined rectangle on the
 * UNO R4 WiFi's 12x8 LED matrix using {@code LedCanvas}.
 */
public final class LedMatrixRectangles {
    public static void main(String[] args) {
        LedMatrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];

        while (true) {
            LedCanvas.clear(frame);
            LedCanvas.fillRect(frame, 3, 1, 6, 6);
            LedCanvas.show(frame);
            Delay.millis(800);
            LedMatrix.clear();

            LedCanvas.clear(frame);
            LedCanvas.drawRect(frame, 3, 1, 6, 6);
            LedCanvas.show(frame);
            Delay.millis(800);
            LedMatrix.clear();

            LedCanvas.clear(frame);
            LedCanvas.fillRect(frame, 1, 2, 10, 4);
            LedCanvas.show(frame);
            Delay.millis(800);
            LedMatrix.clear();

            LedCanvas.clear(frame);
            LedCanvas.drawRect(frame, 1, 2, 10, 4);
            LedCanvas.show(frame);
            Delay.millis(800);
            LedMatrix.clear();
        }
    }
}
