package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.util.Delay;

/** The onboard LED matrix through the display: 12x8 on the UNO R4 WiFi, 13x8 on the UNO Q; the width-aware canvas. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class LedMatrixApi {
    public static void main(String[] args) {
        Display display = MCU.getDisplay();
        display.fillRect(0, 0, display.width(), 2, Display.WHITE);
        display.flush();
        Delay.millis(10);

        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
        LedCanvas.fillRect(frame, 0, 0, LedCanvas.width(), 2);
        LedCanvas.show(frame);
        Delay.millis(10);

        LedCanvas.clear(frame);
        LedCanvas.show(frame);
    }
}
