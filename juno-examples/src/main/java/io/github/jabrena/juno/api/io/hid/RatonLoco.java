package io.github.jabrena.juno.api.io.hid;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.DigitalOutput;
import io.github.jabrena.juno.api.led.LedMatrix;

/**
 * Demonstrates USB HID mouse control on the Arduino UNO R4 WiFi. At startup, the program displays
 * a {@link MouseIcon mouse face} on the board's 12x8 LED matrix. It then repeatedly blinks the
 * built-in LED and moves the connected computer's pointer around a square, travelling right,
 * down, left, and up in five 50-pixel steps per side.
 *
 * <p>The example requires the Arduino {@code Mouse} library, which can be installed with
 * {@code arduino-cli lib install Mouse}. The UNO R4 WiFi supports the native USB connection needed
 * to act as a mouse.
 *
 * <p><strong>Warning:</strong> Once uploaded, this program takes control of the pointer on the
 * computer connected to the board's USB port and continues moving it indefinitely. Disconnect the
 * board or replace the sketch to stop it.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class RatonLoco {
    private static final int LED = 13;
    private static final int MOVE_DELAY = 1000;
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;

    /**
     * Initializes the USB mouse and LED matrix, displays the mouse icon, and runs the cursor
     * movement loop indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(LED);
        Mouse.begin();

        LedMatrix.begin();
        MouseIcon.draw();

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
}
