import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * Counts a one-decimal-digit reading from 0.0 up to 9.9 on the UNO R4 WiFi's 12x8 LED matrix,
 * using the compact 3x5 font in {@code LedCanvas}/{@code LedMatrixFontSmall}. Unlike the
 * 5x7 font used by {@code LedMatrixCountUp}, the 3x5 digits leave room for a decimal point, so
 * "wholeDigit.fractionDigit" fits as a single 9-column group, centered with 1 column to spare on
 * each side.
 */
public final class LedMatrixDecimalCountUp {
    public static void main(String[] args) {
        LedMatrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];

        while (true) {
            int wholeDigit = 0;
            while (wholeDigit <= 9) {
                int fractionDigit = 0;
                while (fractionDigit <= 9) {
                    LedCanvas.clear(frame);
                    LedCanvas.drawDecimal(frame, wholeDigit, fractionDigit, 1, 1);

                    LedCanvas.show(frame);
                    Delay.millis(150);
                    LedMatrix.clear();
                    fractionDigit = fractionDigit + 1;
                }
                wholeDigit = wholeDigit + 1;
            }
        }
    }
}
