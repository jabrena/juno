import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.LedMatrix;
import io.github.jabrena.juno.api.LedMatrixText;

/**
 * Scrolls "Juno, Java for Arduino ONE R4" across the UNO R4 WiFi's 12x8 LED matrix from right to
 * left, one pixel column per tick, using {@code LedMatrixText.drawChar}. Juno v0.1 has no arrays
 * or {@code String}, so the message is a fixed sequence of {@code drawCharAt} calls, one per
 * character, each placed 6 columns apart (5-wide glyph + 1-column gap) and shifted by the current
 * scroll offset; {@code LedCanvas.setPixel} already clips anything outside the 12x8 frame, so
 * characters simply appear at the right edge and disappear off the left edge as the offset shrinks.
 */
public final class LedMatrixScrollingText {
    private static final int CHAR_SPACING = 6;
    private static final int CHAR_COUNT = 29;
    private static final int MESSAGE_WIDTH = CHAR_COUNT * CHAR_SPACING;
    private static final int START_OFFSET = 12;
    private static final int END_OFFSET = -MESSAGE_WIDTH;

    public static void main(String[] args) {
        LedMatrix.begin();

        while (true) {
            int offset = START_OFFSET;
            while (offset >= END_OFFSET) {
                int word0 = drawMessage(0, 0, offset);
                int word1 = drawMessage(0, 1, offset);
                int word2 = drawMessage(0, 2, offset);
                LedMatrix.loadFrame(word0, word1, word2);
                Delay.millis(80);
                LedMatrix.clear();
                offset = offset - 1;
            }
        }
    }

    /** "Juno, Java for Arduino ONE R4", one drawCharAt per character (spaces are skipped: they draw nothing). */
    private static int drawMessage(int word, int wordIndex, int offset) {
        int result = word;
        result = drawCharAt(result, wordIndex, 'J', 0, offset);
        result = drawCharAt(result, wordIndex, 'u', 6, offset);
        result = drawCharAt(result, wordIndex, 'n', 12, offset);
        result = drawCharAt(result, wordIndex, 'o', 18, offset);
        result = drawCharAt(result, wordIndex, ',', 24, offset);
        result = drawCharAt(result, wordIndex, 'J', 36, offset);
        result = drawCharAt(result, wordIndex, 'a', 42, offset);
        result = drawCharAt(result, wordIndex, 'v', 48, offset);
        result = drawCharAt(result, wordIndex, 'a', 54, offset);
        result = drawCharAt(result, wordIndex, 'f', 66, offset);
        result = drawCharAt(result, wordIndex, 'o', 72, offset);
        result = drawCharAt(result, wordIndex, 'r', 78, offset);
        result = drawCharAt(result, wordIndex, 'A', 90, offset);
        result = drawCharAt(result, wordIndex, 'r', 96, offset);
        result = drawCharAt(result, wordIndex, 'd', 102, offset);
        result = drawCharAt(result, wordIndex, 'u', 108, offset);
        result = drawCharAt(result, wordIndex, 'i', 114, offset);
        result = drawCharAt(result, wordIndex, 'n', 120, offset);
        result = drawCharAt(result, wordIndex, 'o', 126, offset);
        result = drawCharAt(result, wordIndex, 'O', 138, offset);
        result = drawCharAt(result, wordIndex, 'N', 144, offset);
        result = drawCharAt(result, wordIndex, 'E', 150, offset);
        result = drawCharAt(result, wordIndex, 'R', 162, offset);
        result = drawCharAt(result, wordIndex, '4', 168, offset);
        return result;
    }

    private static int drawCharAt(int word, int wordIndex, int asciiCode, int baseX, int offset) {
        return LedMatrixText.drawChar(word, wordIndex, asciiCode, baseX + offset, 0);
    }
}
