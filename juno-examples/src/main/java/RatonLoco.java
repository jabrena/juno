import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;
import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;
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
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
        drawMouseIcon(frame);
        LedCanvas.show(frame);

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
     * {@link LedCanvas#fillRect} strips and {@link LedCanvas#setPixel} dots.
     */
    private static void drawMouseIcon(boolean[][] frame) {
        LedCanvas.fillRect(frame, 1, 0, 2, 2); // left ear
        LedCanvas.fillRect(frame, 9, 0, 2, 2); // right ear
        LedCanvas.fillRect(frame, 2, 2, 8, 1); // head top edge
        LedCanvas.fillRect(frame, 2, 3, 1, 2); // left side of head
        LedCanvas.fillRect(frame, 9, 3, 1, 2); // right side of head
        LedCanvas.setPixel(frame, 4, 3);       // left eye
        LedCanvas.setPixel(frame, 7, 3);       // right eye
        LedCanvas.fillRect(frame, 2, 5, 8, 1); // chin
        LedCanvas.fillRect(frame, 5, 7, 2, 1); // nose
    }
}
