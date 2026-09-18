import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * Counts from 1 to 10 on the UNO R4 WiFi's 12x8 LED matrix using the shared 5x7 font in
 * {@code LedCanvas}/{@code LedMatrixFont}. 1-9 are drawn as a single centered digit; 10 is
 * drawn as two digits side by side, since 5 + 1 gap + 5 = 11 fits within the 12-column matrix.
 */
public final class LedMatrixCountUp {
    public static void main(String[] args) {
        LedMatrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];

        while (true) {
            int number = 1;
            while (number <= 10) {
                LedCanvas.clear(frame);
                if (number < 10) {
                    LedCanvas.drawDigit(frame, number, 4, 0);
                } else {
                    LedCanvas.drawDigit(frame, 1, 0, 0);
                    LedCanvas.drawDigit(frame, 0, 6, 0);
                }

                LedCanvas.show(frame);
                Delay.millis(700);
                LedMatrix.clear();
                Delay.millis(120);
                number = number + 1;
            }
        }
    }
}
