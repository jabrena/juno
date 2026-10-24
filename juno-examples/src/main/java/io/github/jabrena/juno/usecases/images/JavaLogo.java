package io.github.jabrena.juno.usecases.images;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.image.JavaLogoImage;

/** Continuously redraws the Java logo on the TFT touch shield. */
@Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
public final class JavaLogo {
    private final JavaLogoImage image = new JavaLogoImage();

    public static void main() {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(500);
        Serial.println("Java logo example");

        JavaLogo logo = new JavaLogo();
        logo.drawRotated180Degrees();

        Serial.println("Java logo example finished");
    }

    private void draw() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        image.draw();
    }

    private void drawRotated180Degrees() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT);
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        image.draw();
    }
}
