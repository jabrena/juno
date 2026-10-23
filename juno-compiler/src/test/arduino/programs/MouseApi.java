package demo;

import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.hid.Mouse;

/** USB HID mouse; needs the Mouse library, and the UNO Q core has no HID.h, so R4 only. */
@Board(ArduinoOneR4WiFiBoard.class)
public final class MouseApi {
    public static void main(String[] args) {
        Mouse mouse = ArduinoOne.getMouse();
        mouse.begin();
        mouse.move(5, -5);
    }
}
