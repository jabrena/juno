package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.hardware.led.LedMatrix;

/** The onboard LED matrix: 12x8 on the UNO R4 WiFi, 13x8 on the UNO Q; raw frames and the width-aware canvas. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class LedMatrixApi {
    public static void main(String[] args) {
        LedMatrix matrix = ArduinoUno.getLedMatrix();
        matrix.begin();
        matrix.loadFrame(0x3184a444, 0x44042081, 0x100a0, 0);
        Delay.millis(10);

        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
        LedCanvas.fillRect(frame, 0, 0, LedCanvas.width(), 2);
        LedCanvas.show(frame);
        Delay.millis(10);

        matrix.clear();
    }
}
