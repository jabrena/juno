import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;
import io.github.jabrena.juno.api.LedCanvas;
import io.github.jabrena.juno.api.LedMatrix;
import io.github.jabrena.juno.api.LedMatrixShapes;
import io.github.jabrena.juno.api.Mouse;

/**
 * Port of <a href="https://github.com/jabrena/raton-loco/blob/main/arduino/raton-loco.ino">
 * raton-loco.ino</a>: blinks the built-in LED, draws a mouse face (ears, eyes, nose — inspired by
 * the {@code (\___/) (\* *') (/ \) ¤} mouse emoticon) on the UNO R4 WiFi's 12x8 LED matrix, then
 * drags the host computer's mouse cursor in a square (right, down, left, up) over USB HID, using
 * the Arduino {@code Mouse} library. Requires a board with native USB (UNO R4 WiFi/Minima) and
 * the {@code Mouse} library ({@code arduino-cli lib install Mouse}). Once uploaded, it takes over
 * the real cursor on whatever computer the board's USB cable is plugged into.
 */
public final class RatonLoco {
    private static final int LED = 13;
    private static final int MOVE_DELAY = 1000;
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(LED);
        Mouse.begin();

        LedMatrix.begin();
        int word0 = drawMouseIcon(0, 0);
        int word1 = drawMouseIcon(0, 1);
        int word2 = drawMouseIcon(0, 2);
        LedMatrix.loadFrame(word0, word1, word2);

        while (true) {
            led.high();
            Delay.millis(100);
            led.low();
            Delay.millis(100);

            // Right
            int i = 0;
            while (i < STEPS_PER_SIDE) {
                Delay.millis(MOVE_DELAY);
                Mouse.move(STEP, 0);
                i = i + 1;
            }
            // Down
            i = 0;
            while (i < STEPS_PER_SIDE) {
                Delay.millis(MOVE_DELAY);
                Mouse.move(0, STEP);
                i = i + 1;
            }
            // Left
            i = 0;
            while (i < STEPS_PER_SIDE) {
                Delay.millis(MOVE_DELAY);
                Mouse.move(-STEP, 0);
                i = i + 1;
            }
            // Up
            i = 0;
            while (i < STEPS_PER_SIDE) {
                Delay.millis(MOVE_DELAY);
                Mouse.move(0, -STEP);
                i = i + 1;
            }
        }
    }

    /**
     * A 12x8 mouse face — round ears, an outlined head, two eyes, and a nose — built from
     * {@link LedMatrixShapes#fillRect} strips and {@link LedCanvas#setPixel} dots since Juno has
     * no arrays to hold a bitmap.
     */
    private static int drawMouseIcon(int word, int wordIndex) {
        int result = word;
        result = LedMatrixShapes.fillRect(result, wordIndex, 1, 0, 2, 2); // left ear
        result = LedMatrixShapes.fillRect(result, wordIndex, 9, 0, 2, 2); // right ear
        result = LedMatrixShapes.fillRect(result, wordIndex, 2, 2, 8, 1); // head top edge
        result = LedMatrixShapes.fillRect(result, wordIndex, 2, 3, 1, 2); // left side of head
        result = LedMatrixShapes.fillRect(result, wordIndex, 9, 3, 1, 2); // right side of head
        result = LedCanvas.setPixel(result, wordIndex, 4, 3);             // left eye
        result = LedCanvas.setPixel(result, wordIndex, 7, 3);             // right eye
        result = LedMatrixShapes.fillRect(result, wordIndex, 2, 5, 8, 1); // chin
        result = LedMatrixShapes.fillRect(result, wordIndex, 5, 7, 2, 1); // nose
        return result;
    }
}
