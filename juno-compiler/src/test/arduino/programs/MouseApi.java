package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.hid.Mouse;

/** USB HID mouse; needs the Mouse library, and the UNO Q core has no HID.h, so R4 only. */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class MouseApi {
    public static void main(String[] args) {
        Mouse mouse = ArduinoUno.getMouse();
        mouse.begin();
        mouse.move(5, -5);
    }
}
