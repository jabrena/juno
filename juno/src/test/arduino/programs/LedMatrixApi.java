package demo;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;

/** The onboard LED matrix: 12x8 on the UNO R4 WiFi, 13x8 on the UNO Q; raw frames and the width-aware canvas. */
public final class LedMatrixApi {
    public static void main(String[] args) {
        LedMatrix.begin();
        LedMatrix.loadFrame(0x3184a444, 0x44042081, 0x100a0, 0);
        Delay.millis(10);

        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
        LedCanvas.fillRect(frame, 0, 0, LedCanvas.width(), 2);
        LedCanvas.show(frame);
        Delay.millis(10);

        LedMatrix.clear();
    }
}
