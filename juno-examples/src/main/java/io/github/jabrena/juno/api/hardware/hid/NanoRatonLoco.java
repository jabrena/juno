package io.github.jabrena.juno.api.hardware.hid;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.hid.Mouse;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * {@link RatonLoco} for the Arduino Nano ESP32: the board acts as a USB HID mouse through its core's TinyUSB stack and
 * moves the connected computer's pointer around a square, five 50-pixel steps per side, blinking the built-in LED
 * between laps. The same USB port stays the serial console, and each side is reported over Serial at 115200 baud, so
 * {@code juno:monitor} shows the HID and serial interfaces working together. No extra library is needed.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.hid.NanoRatonLoco -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 *
 * <p><strong>Warning:</strong> Once uploaded, this program takes control of the pointer on the computer connected to
 * the board's USB port and continues moving it indefinitely. Disconnect the board or replace the sketch to stop it.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoRatonLoco {
    private static final int LED = 13;
    private static final int MOVE_DELAY = 1000;
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;

    private NanoRatonLoco() {
    }

    /**
     * Starts serial and the USB mouse, then moves the pointer around a square forever.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        DigitalOutput led = DigitalOutput.of(LED);
        Mouse mouse = ArduinoNano.getMouse();
        mouse.begin();
        Serial.println("USB mouse started");

        while (true) {
            led.high();
            Delay.millis(100);
            led.low();

            Serial.println("Right");
            side(mouse, STEP, 0);
            Serial.println("Down");
            side(mouse, 0, STEP);
            Serial.println("Left");
            side(mouse, -STEP, 0);
            Serial.println("Up");
            side(mouse, 0, -STEP);
        }
    }

    private static void side(Mouse mouse, int x, int y) {
        int i = 0;
        while (i < STEPS_PER_SIDE) {
            Delay.millis(MOVE_DELAY);
            mouse.move(x, y);
            i = i + 1;
        }
    }
}
