package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.hid.Mouse;

/** USB HID mouse; the Pico 2 W uses the arduino-pico Mouse library with its Pico SDK USB stack. */
@Board({ArduinoUnoR4WiFiBoard.class, RaspberryPico2WifiBoard.class})
public final class MouseApi {
    public static void main(String[] args) {
        Mouse mouse = ArduinoUno.getMouse();
        mouse.begin();
        mouse.move(5, -5);
    }
}
