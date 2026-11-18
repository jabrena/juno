package demo;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Verifies that a grayscale program starts the sensor in grayscale and fills a caller-owned 64x64 buffer. */
@Board(AiThinkerEsp32CamBoard.class)
public final class CameraGrayscaleApi {
    private CameraGrayscaleApi() {
    }

    public static void main(String[] args) {
        Camera camera = MCU.getCamera();
        byte[] pixels = new byte[64 * 64];
        Serial.println(camera.begin());
        Serial.println(camera.captureGrayscale(pixels, 64, 64));
    }
}
