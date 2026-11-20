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
 * Demonstrates USB HID mouse control on the Arduino UNO R4 WiFi, the Nano ESP32 and the Portenta H7. At startup, the UNO R4
 * WiFi displays a {@link MouseIcon mouse face} on its 12x8 LED matrix; the other two have no matrix. All three boards then
 * repeatedly blink the built-in LED and move the connected computer's pointer around a square, travelling right,
 * down, left, and up in five 50-pixel steps per side.
 *
 * <p>The example requires the Arduino {@code Mouse} library, which can be installed with
 * {@code arduino-cli lib install Mouse}; the Nano ESP32 uses its core's TinyUSB mouse and the Portenta H7 its Mbed core's USB HID
 * mouse, so neither needs it. All three boards have the native USB connection needed to act as a mouse.
 *
 * <p><strong>Warning:</strong> Once uploaded, this program takes control of the pointer on the
 * computer connected to the board's USB port and continues moving it indefinitely. Disconnect the
 * board or replace the sketch to stop it.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, ArduinoPortentaH7Board.class, M5StackCardputerBoard.class})
public final class RatonLoco {
    private static final int MOVE_DELAY = 1000;
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;

    /**
     * Initializes the USB mouse, displays the mouse icon on the UNO R4 WiFi's LED matrix, and runs the cursor
     * movement loop indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());
        Mouse mouse = MCU.getMouse();

        //Conditional behaviour if run on R4 Wifi board.
        if (MCU.getBoardModel() == BoardModel.UNO_R4_WIFI) {
            MouseIcon.draw();
        }

        while (true) {
            led.high();
            Delay.millis(100);
            led.low();
            Delay.millis(100);

            moveSide(mouse, STEP, 0);
            moveSide(mouse, 0, STEP);
            moveSide(mouse, -STEP, 0);
            moveSide(mouse, 0, -STEP);
        }
    }

    private static void moveSide(Mouse mouse, int x, int y) {
        for (int step = 0; step < STEPS_PER_SIDE; step++) {
            Delay.millis(MOVE_DELAY);
            mouse.move(x, y);
        }
    }
}
