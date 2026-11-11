package demo;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.RaspberryPico2;
import io.github.jabrena.juno.hardware.hid.Mouse;

/** Pico 2 W USB HID mouse through its board facade. */
@Board(RaspberryPico2WifiBoard.class)
public final class PicoMouseApi {
    public static void main(String[] args) {
        Mouse mouse = RaspberryPico2.getMouse();
        mouse.begin();
        mouse.move(5, -5);
    }
}
