package io.github.jabrena.juno.api.hardware.hid;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.BoardModel;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.hid.Mouse;
import io.github.jabrena.juno.util.Delay;

/**
 * Demonstrates USB HID mouse control on the Arduino UNO R4 WiFi, the Nano ESP32, the Portenta H7 and the M5Stack
 * Cardputer. At startup, the boards with a display show a {@link MouseIcon mouse face}: the UNO R4 WiFi on its 12x8 LED
 * matrix, the Cardputer on its screen. Every board then moves the connected computer's pointer around a square,
 * travelling right, down, left, and up in five 50-pixel steps per side, one step a second, flashing the built-in LED
 * before each step.
 *
 * <p>On the UNO R4 WiFi the example requires the Arduino {@code Mouse} library, which can be installed with
 * {@code arduino-cli lib install Mouse}; the Nano ESP32 and the Cardputer use their core's TinyUSB mouse and the
 * Portenta H7 its Mbed core's USB HID mouse, so they need no extra library. Every board here has the native USB
 * connection needed to act as a mouse.
 *
 * <p><strong>Warning:</strong> Once uploaded, this program takes control of the pointer on the
 * computer connected to the board's USB port and continues moving it indefinitely. Disconnect the
 * board or replace the sketch to stop it.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, ArduinoPortentaH7Board.class, M5StackCardputerBoard.class})
public final class RatonLoco {
    private static final int BLINK_DELAY = 100;
    private static final int MOVE_DELAY = 1000;
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;

    /**
     * Initializes the USB mouse, displays the mouse icon on the boards with a display, and runs the cursor
     * movement loop indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());
        Mouse mouse = MCU.getMouse();

        // Boards with a display show the mouse face: the UNO R4 WiFi on its LED matrix, the Cardputer on its screen.
        if (MCU.getBoardModel() == BoardModel.UNO_R4_WIFI || MCU.getBoardModel() == BoardModel.M5_CARDPUTER) {
            MouseIcon.draw();
        }

        while (true) {
            mouseMovement(mouse, led);
        }
    }

    /** Flashes the built-in LED once. */
    private static void blink(DigitalOutput led) {
        led.high();
        Delay.millis(BLINK_DELAY);
        led.low();
        Delay.millis(BLINK_DELAY);
    }

    /** Walks the pointer once around the square: right, down, left and up. */
    private static void mouseMovement(Mouse mouse, DigitalOutput led) {
        moveSide(mouse, led, STEP, 0);
        moveSide(mouse, led, 0, STEP);
        moveSide(mouse, led, -STEP, 0);
        moveSide(mouse, led, 0, -STEP);
    }

    /** One step a second: the LED flashes, then the pointer moves by ({@code x}, {@code y}). */
    private static void moveSide(Mouse mouse, DigitalOutput led, int x, int y) {
        for (int step = 0; step < STEPS_PER_SIDE; step++) {
            blink(led);
            Delay.millis(MOVE_DELAY - 2 * BLINK_DELAY);
            mouse.move(x, y);
        }
    }
}
