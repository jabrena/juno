import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * Scrolls "Juno, Java for Arduino ONE R4" across the UNO R4 WiFi's 12x8 LED matrix from right to
 * left, one pixel column per tick, using {@code LedCanvas.drawChar}. Juno v0.1 has no
 * {@code String}, so the message is a fixed sequence of {@code drawCharAt} calls, one per
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
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];

        while (true) {
            int offset = START_OFFSET;
            while (offset >= END_OFFSET) {
                LedCanvas.clear(frame);
                drawMessage(frame, offset);
                LedCanvas.show(frame);
                Delay.millis(80);
                LedMatrix.clear();
                offset = offset - 1;
            }
        }
    }

    /** "Juno, Java for Arduino ONE R4", one drawCharAt per character (spaces are skipped: they draw nothing). */
    private static void drawMessage(boolean[][] frame, int offset) {
        drawCharAt(frame, 'J', 0, offset);
        drawCharAt(frame, 'u', 6, offset);
        drawCharAt(frame, 'n', 12, offset);
        drawCharAt(frame, 'o', 18, offset);
        drawCharAt(frame, ',', 24, offset);
        drawCharAt(frame, 'J', 36, offset);
        drawCharAt(frame, 'a', 42, offset);
        drawCharAt(frame, 'v', 48, offset);
        drawCharAt(frame, 'a', 54, offset);
        drawCharAt(frame, 'f', 66, offset);
        drawCharAt(frame, 'o', 72, offset);
        drawCharAt(frame, 'r', 78, offset);
        drawCharAt(frame, 'A', 90, offset);
        drawCharAt(frame, 'r', 96, offset);
        drawCharAt(frame, 'd', 102, offset);
        drawCharAt(frame, 'u', 108, offset);
        drawCharAt(frame, 'i', 114, offset);
        drawCharAt(frame, 'n', 120, offset);
        drawCharAt(frame, 'o', 126, offset);
        drawCharAt(frame, 'O', 138, offset);
        drawCharAt(frame, 'N', 144, offset);
        drawCharAt(frame, 'E', 150, offset);
        drawCharAt(frame, 'R', 162, offset);
        drawCharAt(frame, '4', 168, offset);
    }

    private static void drawCharAt(boolean[][] frame, int asciiCode, int baseX, int offset) {
        LedCanvas.drawChar(frame, asciiCode, baseX + offset, 0);
    }
}
