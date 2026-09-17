import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.LedMatrix;
import io.github.jabrena.juno.api.LedMatrixText;

/**
 * Cycles the full printable-ASCII font in {@code LedMatrixFontAscii} one character at a time on
 * the UNO R4 WiFi's 12x8 LED matrix using {@code LedMatrixText.drawChar}: digits 0-9, then
 * uppercase A-Z, then lowercase a-z, then the punctuation/symbol characters, in that order.
 */
public final class LedMatrixAsciiScroll {
    public static void main(String[] args) {
        LedMatrix.begin();

        while (true) {
            for (int code = '0'; code <= '9'; code++) {
                showChar(code);
            }
            for (int code = 'A'; code <= 'Z'; code++) {
                showChar(code);
            }
            for (int code = 'a'; code <= 'z'; code++) {
                showChar(code);
            }
            for (int code = '!'; code <= '/'; code++) {
                showChar(code);
            }
            for (int code = ':'; code <= '@'; code++) {
                showChar(code);
            }
            for (int code = '['; code <= '`'; code++) {
                showChar(code);
            }
            for (int code = '{'; code <= '~'; code++) {
                showChar(code);
            }
        }
    }

    private static void showChar(int asciiCode) {
        int word0 = LedMatrixText.drawChar(0, 0, asciiCode, 4, 0);
        int word1 = LedMatrixText.drawChar(0, 1, asciiCode, 4, 0);
        int word2 = LedMatrixText.drawChar(0, 2, asciiCode, 4, 0);
        LedMatrix.loadFrame(word0, word1, word2);
        Delay.millis(450);
        LedMatrix.clear();
        Delay.millis(80);
    }
}
