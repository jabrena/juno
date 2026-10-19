package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.hardware.hid.Mouse;

/** USB HID mouse; needs the Mouse library, and the UNO Q core has no HID.h, so R4 only. */
@Board(ArduinoUnoR4WiFi.class)
public final class MouseApi {
    public static void main(String[] args) {
        Mouse mouse = MCU.getMouse();
        mouse.begin();
        mouse.move(5, -5);
    }
}
