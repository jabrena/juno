package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

/** The onboard LED matrix through the display: 12x8 on the UNO R4 WiFi, 13x8 on the UNO Q. */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class LedMatrixApi {
    public static void main(String[] args) {
        Display display = MCU.getDisplay();
        display.fillRect(0, 0, display.width(), 2, Display.WHITE);
        display.flush();
        Delay.millis(10);

        display.fillScreen(Display.BLACK);
        display.flush();
    }
}
