import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.LedMatrix;
import io.github.jabrena.juno.api.LedMatrixText;

/**
 * Counts from 1 to 10 on the UNO R4 WiFi's 12x8 LED matrix using the shared 5x7 font in
 * {@code LedMatrixText}/{@code LedMatrixFont}. 1-9 are drawn as a single centered digit; 10 is
 * drawn as two digits side by side, since 5 + 1 gap + 5 = 11 fits within the 12-column matrix.
 */
public final class LedMatrixCountUp {
    public static void main(String[] args) {
        LedMatrix.begin();

        while (true) {
            int number = 1;
            while (number <= 10) {
                int word0 = 0;
                int word1 = 0;
                int word2 = 0;
                if (number < 10) {
                    word0 = LedMatrixText.drawDigit(word0, 0, number, 4, 0);
                    word1 = LedMatrixText.drawDigit(word1, 1, number, 4, 0);
                    word2 = LedMatrixText.drawDigit(word2, 2, number, 4, 0);
                } else {
                    word0 = LedMatrixText.drawDigit(word0, 0, 1, 0, 0);
                    word1 = LedMatrixText.drawDigit(word1, 1, 1, 0, 0);
                    word2 = LedMatrixText.drawDigit(word2, 2, 1, 0, 0);
                    word0 = LedMatrixText.drawDigit(word0, 0, 0, 6, 0);
                    word1 = LedMatrixText.drawDigit(word1, 1, 0, 6, 0);
                    word2 = LedMatrixText.drawDigit(word2, 2, 0, 6, 0);
                }

                LedMatrix.loadFrame(word0, word1, word2);
                Delay.millis(700);
                LedMatrix.clear();
                Delay.millis(120);
                number = number + 1;
            }
        }
    }
}
