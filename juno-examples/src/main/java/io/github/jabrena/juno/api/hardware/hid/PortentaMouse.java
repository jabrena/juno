package io.github.jabrena.juno.api.hardware.hid;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.hid.Mouse;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Moves the Portenta H7's native USB HID mouse while reporting progress over the same USB serial link. */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaMouse {
    private static final int STEP = 40;
    private static final int SIDE_STEPS = 5;

    private PortentaMouse() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Mouse mouse = MCU.getMouse();
        Serial.println("Portenta HID mouse started");
        while (true) {
            moveSide(mouse, STEP, 0, "right");
            moveSide(mouse, 0, STEP, "down");
            moveSide(mouse, -STEP, 0, "left");
            moveSide(mouse, 0, -STEP, "up");
        }
    }

    private static void moveSide(Mouse mouse, int x, int y, String direction) {
        Serial.println(direction);
        for (int step = 0; step < SIDE_STEPS; step++) {
            mouse.move(x, y);
            Delay.millis(250);
        }
    }
}
