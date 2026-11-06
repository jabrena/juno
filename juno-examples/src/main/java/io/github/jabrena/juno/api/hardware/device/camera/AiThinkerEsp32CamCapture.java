package io.github.jabrena.juno.api.hardware.device.camera;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Esp32Cam;
import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Captures a JPEG frame from the AI Thinker ESP32-CAM's camera every few seconds and prints its length and whether it
 * starts with the JPEG start-of-image marker. The frame buffer is allocated once, before the loop.
 */
@Board(AiThinkerEsp32CamBoard.class)
public final class AiThinkerEsp32CamCapture {
    private static final Camera CAMERA = Esp32Cam.getCamera();
    private static final int FRAME_CAPACITY = 6144;
    private static final int CAPTURE_INTERVAL_MILLIS = 3000;

    private AiThinkerEsp32CamCapture() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("AiThinkerEsp32CamCapture started");
        if (!CAMERA.begin()) {
            Serial.println("Camera initialization failed");
            return;
        }
        byte[] frame = new byte[FRAME_CAPACITY];
        while (true) {
            int length = CAMERA.capture(frame);
            if (length < 0) {
                Serial.println("No frame captured");
            } else {
                Serial.print("JPEG bytes=");
                Serial.print(length);
                Serial.println(startsWithJpegMarker(frame) ? " SOI=ok" : " SOI=bad");
            }
            Delay.millis(CAPTURE_INTERVAL_MILLIS);
        }
    }

    private static boolean startsWithJpegMarker(byte[] frame) {
        return frame[0] == (byte) 0xFF && frame[1] == (byte) 0xD8;
    }
}
