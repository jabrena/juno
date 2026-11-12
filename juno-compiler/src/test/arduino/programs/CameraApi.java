package demo;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Esp32Cam;
import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Verifies that camera frame buffers remain driver-owned while callers receive copied bytes. */
@Board(AiThinkerEsp32CamBoard.class)
public final class CameraApi {
    private CameraApi() {
    }

    public static void main(String[] args) {
        Camera camera = Esp32Cam.getCamera();
        byte[] frame = new byte[4096];
        Serial.println(camera.begin());
        Serial.println(camera.capture(frame));
    }
}
