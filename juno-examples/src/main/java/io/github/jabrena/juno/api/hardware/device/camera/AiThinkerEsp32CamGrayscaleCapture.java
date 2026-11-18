package io.github.jabrena.juno.api.hardware.device.camera;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * Captures a 64x64 grayscale frame from the AI Thinker ESP32-CAM's camera every few seconds and prints a checksum of its
 * pixels and the milliseconds the capture took. The pixel buffer is allocated once, before the loop.
 */
@Board(AiThinkerEsp32CamBoard.class)
public final class AiThinkerEsp32CamGrayscaleCapture {
    private static final Camera CAMERA = MCU.getCamera();
    private static final Clock CLOCK = MCU.getClock();
    private static final int SIDE = 64;
    private static final int CAPTURE_INTERVAL_MILLIS = 3000;

    private AiThinkerEsp32CamGrayscaleCapture() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("AiThinkerEsp32CamGrayscaleCapture started");
        if (!CAMERA.begin()) {
            Serial.println("Camera initialization failed");
            return;
        }
        byte[] pixels = new byte[SIDE * SIDE];
        while (true) {
            int start = CLOCK.millis();
            boolean captured = CAMERA.captureGrayscale(pixels, SIDE, SIDE);
            int elapsed = CLOCK.millis() - start;
            if (!captured) {
                Serial.println("No frame captured");
            } else {
                Serial.print("GRAY ");
                Serial.print(SIDE);
                Serial.print("x");
                Serial.print(SIDE);
                Serial.print(" checksum=");
                Serial.print(checksum(pixels));
                Serial.print(" ms=");
                Serial.println(elapsed);
            }
            Delay.millis(CAPTURE_INTERVAL_MILLIS);
        }
    }

    private static int checksum(byte[] pixels) {
        int sum = 0;
        for (int i = 0; i < pixels.length; i++) {
            sum += pixels[i] & 0xFF;
        }
        return sum;
    }
}
